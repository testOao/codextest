package com.example.workflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Test void acceptsUploadAndReturnsReport() throws Exception {
        var xml=java.nio.file.Files.readAllBytes(java.nio.file.Path.of("examples/approval.xpdl"));
        mvc.perform(multipart("/api/analyze").file(new MockMultipartFile("file","approval.xpdl","application/xml",xml)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.processes[0].activities.length()").value(2));
    }
    @Test void rejectsUnsafeXml() throws Exception {
        mvc.perform(post("/api/analyze").contentType("application/xml").content("<!DOCTYPE Package><Package/>"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }
    @Test void servesUploadPage() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Joget workflow analyzer")));
    }
}
