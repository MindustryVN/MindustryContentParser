package mindustrytool.mindustrycontentparser.service;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import arc.graphics.g2d.Draw;
import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.io.Reads;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.ContentType;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.graphics.Drawf;
import mindustry.io.JsonIO;
import mindustry.io.SaveFileReader;
import mindustry.io.TypeIO;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.legacy.LegacyBlock;
import mindustry.world.blocks.power.LightBlock;
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.blocks.storage.Unloader;
import mindustrytool.mindustrycontentparser.types.request.SchematicPreviewRequest;
import mindustrytool.mindustrycontentparser.types.response.SchematicItemRequirementDto;
import mindustrytool.mindustrycontentparser.types.response.SchematicPreviewResult;
import mindustrytool.mindustrycontentparser.utils.ApiError;
import mindustrytool.mindustrycontentparser.utils.DrawBatch;
import mindustrytool.mindustrycontentparser.utils.Utils;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchematicService {

    private final AssetsService assetsService;

    public Mono<SchematicPreviewResult> getPreview(SchematicPreviewRequest request) {
        var result = getPreview(request.getData());

        return result;
    }

    private Mono<SchematicPreviewResult> getPreview(byte[] data) {
        var schematic = parseDecodedSchematic(data);
        BufferedImage image = getSchematicImage(schematic);

        String str = Utils.imageToBase64(image);

        SchematicPreviewResult result = new SchematicPreviewResult()//
                .setName(schematic.name())//
                .setDescription(schematic.description())//
                .setImage(str)//
                .setLabels(schematic.labels.list())//
                .setWidth(schematic.width)//
                .setHeight(schematic.height)//
                .setRequirements(SchematicItemRequirementDto.from(schematic.requirements()));

        if (schematic.mod != null) {
            result.setMod(schematic.mod.name);
        }

        return Mono.just(result);
    }

    private synchronized Schematic parseDecodedSchematic(byte[] data) {
        try {
            return read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read schematic from byte", e);
        }
    }

    private synchronized Schematic read(InputStream input) throws IOException {
        byte[] header = { 'm', 's', 'c', 'h' };
        for (byte b : header) {
            if (input.read() != b) {
                throw new ApiError(HttpStatus.BAD_REQUEST, "Not a schematic file (missing header).");
            }
        }

        int ver = input.read();

        try (DataInputStream stream = new DataInputStream(new InflaterInputStream(input))) {
            short width = stream.readShort(), height = stream.readShort();
            if (width > 1024 || height > 1024)
                throw new IOException("Invalid schematic: Too large (max possible size is 1024x1024)");

            StringMap map = new StringMap();
            int tags = stream.readUnsignedByte();
            for (int i = 0; i < tags; i++) {
                map.put(stream.readUTF(), stream.readUTF());
            }

            String[] labels = null;

            try {
                labels = JsonIO.read(String[].class, map.get("labels", "[]"));
            } catch (Exception ignored) {
            }

            IntMap<Block> blocks = new IntMap<>();
            byte length = stream.readByte();
            for (int i = 0; i < length; i++) {
                String name = stream.readUTF();
                Block block = Vars.content.getByName(ContentType.block, SaveFileReader.fallback.get(name, name));
                blocks.put(i, block == null || block instanceof LegacyBlock ? Blocks.air : block);
            }

            int total = stream.readInt();

            if (total > 1024 * 1024)
                throw new IOException("Invalid schematic: Too many blocks.");

            Seq<Stile> tiles = new Seq<>(total);
            for (int i = 0; i < total; i++) {
                Block block = blocks.get(stream.readByte());
                int position = stream.readInt();
                Object config = ver == 0 ? //
                        mapConfig(block, stream.readInt(), position) : TypeIO.readObject(Reads.get(stream));
                byte rotation = stream.readByte();
                if (block != Blocks.air) {
                    tiles.add(new Stile(block, Point2.x(position), Point2.y(position), config, rotation));
                }
            }

            Schematic out = new Schematic(tiles, map, width, height);
            if (labels != null)
                out.labels.addAll(labels);
            return out;
        }
    }

    private static Object mapConfig(Block block, int value, int position) {
        if (block instanceof Sorter || block instanceof Unloader || block instanceof ItemSource)
            return Vars.content.item(value);
        if (block instanceof LiquidSource)
            return Vars.content.liquid(value);
        if (block instanceof MassDriver || block instanceof ItemBridge)
            return Point2.unpack(value).sub(Point2.x(position), Point2.y(position));
        if (block instanceof LightBlock)
            return value;

        return null;
    }

    private synchronized BufferedImage getSchematicImage(Schematic schematic) {

        log.info("Generate image for: {}", schematic.name());

        BufferedImage image = new BufferedImage(schematic.width * 32, schematic.height * 32, BufferedImage.TYPE_INT_ARGB);
        Seq<BuildPlan> plans = schematic.tiles.map(t -> new BuildPlan(t.x, t.y, t.rotation, t.block, t.config));

        DrawBatch.currentGraphics = image.createGraphics();
        DrawBatch.currentImage = image;

        Draw.reset();
        plans.each(plan -> Drawf.squareShadow(plan.drawx(), plan.drawy(), plan.block.size * 16f, 0.8f));
        plans.each(req -> {
            req.animScale = 1f;
            req.worldContext = false;
            req.block.drawPlanRegion(req, plans::each);
            Draw.reset();
        });
        plans.each(plan -> plan.block.drawPlanConfigTop(plan, plans::each));

        int size = 2 + Math.max(Math.max(schematic.width, schematic.height), 16);
        final int offX = (size - schematic.width) / 2;
        final int offY = (size - schematic.height) / 2;

        BufferedImage background = new BufferedImage(size * 32, size * 32, BufferedImage.TYPE_INT_ARGB);

        DrawBatch.currentGraphics.dispose();
        DrawBatch.currentGraphics = background.createGraphics();
        DrawBatch.currentImage = background;

        Draw.reset();
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++)
                Draw.rect("metal-floor", x * 8f, y * 8f);

        AffineTransform transform = new AffineTransform();
        transform.translate(offX * 32, offY * 32);
        DrawBatch.currentGraphics.setTransform(transform);
        DrawBatch.currentGraphics.drawImage(image, 0, 0, null);

        assetsService.reset();

        return background;
    }
}
