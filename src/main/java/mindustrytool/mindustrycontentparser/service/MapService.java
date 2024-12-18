package mindustrytool.mindustrycontentparser.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import arc.util.io.CounterInputStream;
import lombok.extern.slf4j.Slf4j;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.world.Block;
import mindustry.world.CachedTile;
import mindustry.world.Tile;
import mindustry.world.WorldContext;
import mindustrytool.mindustrycontentparser.types.request.MapPreviewRequest;
import mindustrytool.mindustrycontentparser.types.response.MapPreviewResult;
import mindustrytool.mindustrycontentparser.utils.ApiError;
import mindustrytool.mindustrycontentparser.utils.Utils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class MapService {

    private static Color co = new Color();
    private static int SCALE = 8;

    public Mono<MapPreviewResult> getPreview(MapPreviewRequest request) {
        Map map = parseDecodedMap(Utils.decode(request.getData()));

        var result = new MapPreviewResult()//
                .setAuthor(map.author)//
                .setDescription(map.description)//
                .setHeight(map.height)//
                .setImage(Utils.imageToBase64(map.image))//
                .setName(map.name)//
                .setTags(map.tags)//
                .setWidth(map.width);

        return Mono.just(result);
    }

    public synchronized Map parseDecodedMap(byte[] data) {
        try {
            return readMap(new ByteArrayInputStream(data));
        } catch (IOException e) {
            log.error("Can not read map", e);
            throw new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read map");
        }
    }

    private synchronized Map readMap(InputStream is) throws IOException {
        try (InputStream ifs = new InflaterInputStream(is); CounterInputStream counter = new CounterInputStream(ifs); DataInputStream stream = new DataInputStream(counter)) {

            Map out = new Map();

            SaveIO.readHeader(stream);
            int version = stream.readInt();
            SaveVersion ver = SaveIO.getSaveWriter(version);

            StringMap[] metaOut = { null };
            ver.region("meta", stream, counter, in -> metaOut[0] = ver.readStringMap(in));

            StringMap meta = metaOut[0];

            out.name = meta.get("name", "Unknown");
            out.author = meta.get("author");
            out.description = meta.get("description");
            out.tags = meta;

            out.description = out.description == null ? "" : out.description;

            int width = meta.getInt("width"), height = meta.getInt("height");

            out.width = width;
            out.height = height;

            var floors = new BufferedImage(width * SCALE + SCALE, height * SCALE + SCALE, BufferedImage.TYPE_INT_ARGB);
            var walls = new BufferedImage(width * SCALE + SCALE, height * SCALE + SCALE, BufferedImage.TYPE_INT_ARGB);

            var fgraphics = floors.createGraphics();

            var jcolor = new java.awt.Color(0, 0, 0, 64);
            int black = 255;
            CachedTile tile = new CachedTile() {
                @Override
                public void setBlock(Block type) {
                    super.setBlock(type);

                    int c = conv(MapIO.colorFor(block(), Blocks.air, Blocks.air, team()));
                    if (c != black && c != 0) {
                        try {

                            fgraphics.setColor(jcolor);
                            fgraphics.drawRect(x * SCALE, floors.getHeight() - SCALE - y * SCALE - SCALE, SCALE, SCALE);
                        } catch (Exception e) {
                            log.error("Can not read map", e);
                        }
                    }
                }
            };

            ver.region("content", stream, counter, ver::readContentHeader);
            ver.region("preview_map", stream, counter, in -> ver.readMap(in, new WorldContext() {
                @Override
                public void resize(int width, int height) {
                }

                @Override
                public boolean isGenerating() {
                    return false;
                }

                @Override
                public void begin() {
                    Vars.world.setGenerating(true);
                }

                @Override
                public void end() {
                    Vars.world.setGenerating(false);
                }

                @Override
                public void onReadBuilding() {
                    // read team colors
                    if (tile.build != null) {
                        int c = tile.build.team.color.argb8888();
                        int size = tile.block().size * SCALE;
                        int offsetx = -(size - SCALE) / 2;
                        int offsety = -(size - SCALE) / 2;

                        try {
                            for (int dx = 0; dx < size; dx++) {
                                for (int dy = 0; dy < size; dy++) {
                                    int drawx = tile.x * SCALE + dx + offsetx, drawy = tile.y * SCALE + dy + offsety;
                                    walls.setRGB(drawx, floors.getHeight() - SCALE - drawy, c);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Can not read map", e);
                        }
                    }
                }

                @Override
                public Tile tile(int index) {
                    tile.x = (short) (index % width);
                    tile.y = (short) (index / width);
                    return tile;
                }

                @Override
                public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                    if (overlayID != 0) {
                        var color = conv(MapIO.colorFor(Blocks.air, Blocks.air, Vars.content.block(overlayID), Team.derelict));
                        for (int offsetX = 0; offsetX < SCALE; offsetX++) {
                            for (int offsetY = 0; offsetY < SCALE; offsetY++) {
                                try {
                                    floors.setRGB(x * SCALE + offsetX, floors.getHeight() - SCALE - y * SCALE - offsetY, color);
                                } catch (Exception e) {
                                    log.error("Can not read map", e);
                                }
                            }
                        }
                    } else {
                        var color = conv(MapIO.colorFor(Blocks.air, Vars.content.block(floorID), Blocks.air, Team.derelict));
                        for (int offsetX = 0; offsetX < SCALE; offsetX++) {
                            for (int offsetY = 0; offsetY < SCALE; offsetY++) {
                                try {
                                    floors.setRGB(x * SCALE + offsetX, floors.getHeight() - SCALE - y * SCALE - offsetY, color);
                                } catch (Exception e) {
                                    log.error("Can not read map", e);
                                }
                            }
                        }
                    }
                    return tile;
                }
            }));

            fgraphics.drawImage(walls, 0, 0, null);
            fgraphics.dispose();

            out.image = floors;

            return out;

        } finally {
            Vars.content.setTemporaryMapper(null);
        }
    }

    static int conv(int rgba) {
        return co.set(rgba).argb8888();
    }

    public static class Map {
        public String name, author, description;
        public int height, width;
        public ObjectMap<String, String> tags = new ObjectMap<>();
        public BufferedImage image;
    }
}
