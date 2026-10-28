package com.example.workflow;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
class AnalyzerTest {
    private final Analyzer analyzer=new Analyzer();
    private Analyzer.Report parse(String body) { return analyzer.analyze(body.getBytes(StandardCharsets.UTF_8)); }
    @Test void parsesNamespacedPackageAndParticipantScope() throws Exception {
        var report=analyzer.analyze(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("examples/approval.xpdl")));
        assertThat(report.processes()).hasSize(1);
        var p=report.processes().get(0);
        assertThat(p.activities()).hasSize(2); assertThat(p.findings()).isEmpty();
        assertThat(p.startCandidates()).containsExactly("submit"); assertThat(p.endCandidates()).containsExactly("review");
    }
    @Test void detectsBrokenLinksDuplicateIdsAndUnreachableCycles() {
        var p=parse("<Package><WorkflowProcesses><WorkflowProcess Id='p'><Activities><Activity Id='a'/><Activity Id='b'/><Activity Id='c'/><Activity Id='a'/></Activities><Transitions><Transition Id='t' From='b' To='c'/><Transition Id='u' From='c' To='b'/><Transition Id='v' From='a' To='missing'/></Transitions></WorkflowProcess></WorkflowProcesses></Package>").processes().get(0);
        assertThat(p.findings()).extracting(Analyzer.Finding::code).contains("INVALID_ID","BROKEN_TRANSITION","UNREACHABLE");
    }
    @Test void refusesExternalEntitiesAndMalformedXml() {
        assertThatThrownBy(()->parse("<!DOCTYPE Package [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><Package>&x;</Package>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parse("<Package>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parse("<other/>" )).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reportsMissingParticipantAndExternalSubprocess() {
        var p=parse("<Package><WorkflowProcesses><WorkflowProcess Id='p'><Activities><Activity Id='a'><Performer>unknown</Performer><Implementation><SubFlow Id='external'/></Implementation></Activity></Activities></WorkflowProcess></WorkflowProcesses></Package>").processes().get(0);
        assertThat(p.findings()).extracting(Analyzer.Finding::code).containsExactly("PERFORMER_UNRESOLVED","SUBFLOW_UNRESOLVED");
    }
}
