package mindustrytool.mindustrycontentparser.types.request;

import org.springframework.http.codec.multipart.FilePart;

import lombok.Data;

@Data
public class SchematicPreviewRequest {
    private String code;
    private FilePart file;
}
