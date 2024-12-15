package mindustrytool.mindustrycontentparser.types.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import mindustry.type.ItemSeq;

public record SchematicItemRequirementDto(String name, String color, short amount) {

    @JsonIgnore
    public static List<SchematicItemRequirementDto> from(ItemSeq itemSeq) {
        return List.of(itemSeq//
                .toArray())//
                .stream()//
                .map((r) -> new SchematicItemRequirementDto(r.item.name, r.item.color.toString(), (short) r.amount))//
                .toList();
    }
}
