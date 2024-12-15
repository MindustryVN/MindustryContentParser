package mindustrytool.mindustrycontentparser.types.response;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SchematicPreviewResult {

    String name;
    String description;
    String image;
    int width, height;
    List<String> labels;
    String mod;
    List<SchematicItemRequirementDto> requirements;

}
