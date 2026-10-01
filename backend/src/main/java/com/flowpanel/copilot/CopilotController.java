package com.flowpanel.copilot;

import com.flowpanel.copilot.CopilotService.CopilotAnswer;
import com.flowpanel.copilot.DocumentService.DocumentView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "copilot")
public class CopilotController {

    public record AskRequest(String question) {
    }

    private final CopilotService copilot;
    private final DocumentService documents;

    public CopilotController(CopilotService copilot, DocumentService documents) {
        this.copilot = copilot;
        this.documents = documents;
    }

    @PostMapping("/copilot/ask")
    public CopilotAnswer ask(@RequestBody AskRequest request) {
        return copilot.ask(request.question());
    }

    @GetMapping("/documents")
    public List<DocumentView> list() {
        return documents.list();
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(@RequestPart("file") MultipartFile file,
                               @RequestParam(value = "title", required = false) String title) throws IOException {
        return documents.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes(), title);
    }
}
