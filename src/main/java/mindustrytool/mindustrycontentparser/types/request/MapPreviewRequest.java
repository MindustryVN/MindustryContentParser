package mindustrytool.mindustrycontentparser.types.request;

import org.springframework.http.codec.multipart.FilePart;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MapPreviewRequest {
    @NotNull
    private FilePart file;

}
