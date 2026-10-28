package com.example.workflow;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

@Service
public class Analyzer {
    public record Finding(String severity, String code, String elementId, String message) {}
    public record Activity(String id, String name, String type, String performer, String subprocess) {}
    public record Transition(String id, String from, String to, String conditionType, String condition) {}
    public record ProcessReport(String id, String name, List<Activity> activities, List<Transition> transitions,
                                List<String> participants, List<String> startCandidates, List<String> endCandidates,
                                List<Finding> findings) {}
    public record Report(String packageId, String name, String namespace, List<ProcessReport> processes,
                         List<Finding> findings) {}

    public Report analyze(byte[] xml) {
        if (xml.length == 0 || xml.length > 2_000_000) throw new IllegalArgumentException("XML must contain between 1 byte and 2 MB.");
        final Document doc;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            doc = builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid or unsafe XML. Supply XPDL without a DOCTYPE."); }
        var root = doc.getDocumentElement();
        if (!"Package".equals(local(root))) throw new IllegalArgumentException("Expected an XPDL Package root element.");
        var findings = new ArrayList<Finding>();
        var processes = children(container(root, "WorkflowProcesses"), "WorkflowProcess");
        checkIds(processes, findings, "process");
        Set<String> processIds = ids(processes);
        var reports = new ArrayList<ProcessReport>();
        for (var process : processes) reports.add(process(process, root, processIds));
        if (processes.isEmpty()) findings.add(new Finding("ERROR", "NO_PROCESSES", "", "Package contains no workflow processes."));
        return new Report(root.getAttribute("Id"), root.getAttribute("Name"), root.getNamespaceURI(), reports, findings);
    }
    private ProcessReport process(Element process, Element root, Set<String> processIds) {
        var findings = new ArrayList<Finding>();
        var elements = children(container(process,"Activities"),"Activity");
        var links = children(container(process,"Transitions"),"Transition");
        checkIds(elements,findings,"activity"); checkIds(links,findings,"transition");
        Set<String> activityIds = ids(elements);
        var participantElements = new ArrayList<>(children(container(root,"Participants"),"Participant"));
        participantElements.addAll(children(container(process,"Participants"),"Participant"));
        Set<String> participants = ids(participantElements);
        var activities = new ArrayList<Activity>();
        var transitions = new ArrayList<Transition>();
        Map<String,Set<String>> next = new LinkedHashMap<>();
        var incoming = new HashSet<String>();
        for (var id : activityIds) next.put(id,new LinkedHashSet<>());
        for (var e : elements) {
            String id=e.getAttribute("Id"), performer=text(container(e,"Performer"));
            var implementation=container(e,"Implementation");
            var sub=container(implementation,"SubFlow");
            String subId=sub==null?"":sub.getAttribute("Id");
            String type=container(e,"Route")!=null?"route":sub!=null?"subflow":container(implementation,"Tool")!=null?"tool":"activity";
            activities.add(new Activity(id,e.getAttribute("Name"),type,performer,subId));
            if (!performer.isBlank() && !participants.contains(performer)) findings.add(new Finding("WARNING","PERFORMER_UNRESOLVED",id,"Performer is not a declared participant; verify whether it is a supported dynamic expression: " + performer));
            if (sub!=null && !processIds.contains(subId)) findings.add(new Finding("WARNING","SUBFLOW_UNRESOLVED",id,"Subflow is not in this package; verify external package resolution: " + subId));
        }
        for (var e : links) {
            String id=e.getAttribute("Id"), from=e.getAttribute("From"), to=e.getAttribute("To");
            var condition=container(e,"Condition");
            transitions.add(new Transition(id,from,to,condition==null?"":condition.getAttribute("Type"),text(condition)));
            if (!activityIds.contains(from)||!activityIds.contains(to)) findings.add(new Finding("ERROR","BROKEN_TRANSITION",id,"Transition references an unknown source or target activity."));
            else { next.get(from).add(to); incoming.add(to); }
        }
        var starts=activityIds.stream().filter(id->!incoming.contains(id)).toList();
        var ends=activityIds.stream().filter(id->next.get(id).isEmpty()).toList();
        if(elements.isEmpty()) findings.add(new Finding("ERROR","NO_ACTIVITIES",process.getAttribute("Id"),"Process contains no activities."));
        if(!elements.isEmpty() && starts.isEmpty()) findings.add(new Finding("WARNING","NO_START_CANDIDATE",process.getAttribute("Id"),"No activity without incoming transitions. Check explicit start configuration and cycles."));
        if(!elements.isEmpty() && ends.isEmpty()) findings.add(new Finding("WARNING","NO_END_CANDIDATE",process.getAttribute("Id"),"No activity without outgoing transitions. Check termination and cycles."));
        var reachable=new HashSet<String>(); var queue=new ArrayDeque<>(starts);
        while(!queue.isEmpty()) { var id=queue.remove(); if(reachable.add(id)) queue.addAll(next.get(id)); }
        if(!starts.isEmpty()) for(var id:activityIds) if(!reachable.contains(id)) findings.add(new Finding("WARNING","UNREACHABLE",id,"Activity cannot be reached from any graph start candidate."));
        return new ProcessReport(process.getAttribute("Id"),process.getAttribute("Name"),activities,transitions,new ArrayList<>(participants),starts,ends,findings);
    }
    private static String local(Element e) { return e.getLocalName()==null?e.getTagName():e.getLocalName(); }
    private static List<Element> children(Element parent,String name) {
        var result=new ArrayList<Element>();
        if(parent!=null) for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e && local(e).equals(name) && Objects.equals(e.getNamespaceURI(),parent.getNamespaceURI())) result.add(e);
        return result;
    }
    private static Element container(Element parent,String name) { return children(parent,name).stream().findFirst().orElse(null); }
    private static String text(Element e) { return e==null?"":e.getTextContent().trim(); }
    private static Set<String> ids(List<Element> elements) { var result=new LinkedHashSet<String>(); for(var e:elements) if(!e.getAttribute("Id").isBlank()) result.add(e.getAttribute("Id")); return result; }
    private static void checkIds(List<Element> elements,List<Finding> findings,String kind) {
        var seen=new HashSet<String>(); for(var e:elements) { var id=e.getAttribute("Id"); if(id.isBlank()||!seen.add(id)) findings.add(new Finding("ERROR","INVALID_ID",id,"Missing or duplicate " + kind + " ID.")); }
    }
}
