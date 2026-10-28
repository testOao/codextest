package com.example.workflow;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.io.IOException;
@RestController
public class AnalyzeController {
    private final Analyzer analyzer;
    public AnalyzeController(Analyzer analyzer) { this.analyzer=analyzer; }
    @PostMapping(value="/api/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Analyzer.Report upload(@RequestParam("file") MultipartFile file) throws IOException { return analyzer.analyze(file.getBytes()); }
    @PostMapping(value="/api/analyze",consumes={MediaType.APPLICATION_XML_VALUE,MediaType.TEXT_XML_VALUE})
    public Analyzer.Report xml(@RequestBody byte[] xml) { return analyzer.analyze(xml); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String,String> invalid(IllegalArgumentException e) { return java.util.Map.of("error",e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public java.util.Map<String,String> tooLarge() { return java.util.Map.of("error","Upload exceeds the 2 MB limit."); }
}
