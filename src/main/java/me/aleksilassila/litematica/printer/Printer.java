package me.aleksilassila.litematica.printer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.util.JsonUtils;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.config.Hotkeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    @NotNull
    public final ClientPlayerEntity player;
    public final MinecraftClient client;


    // Curren region being placed.
    private int currentRegionIndex = 0;
    // List of all the regions.
    private List<Region> regions = new ArrayList<>();
    // Total blocks found in scan.
    private int blocksFound = 0;
    // Current index of the block in currentRegion that is being placed.
    private int currentBlockIndex = 0;
    // Scanning is on or not
    private boolean scanning = false;
    // Whether the scan is complete or not.
    private boolean scanComplete = false;
    // Currently scanning region.
    private int currentScanRegionIndex = 0;

    private BlockPos scanPlacementCurrentPos = null;
    private int totalBlocksToScan = 0;
    private int totalBlocksScanned = 0;
    private int totalBlocksPlaced = 0;
    private WorldSchematic worldSchematic;
    private net.minecraft.world.World world;

    private long lastSaveTime = 0;

    private ArrayList<BlockPos> fillEmptyList = new ArrayList<>();



    public Printer(@NotNull MinecraftClient client, @NotNull ClientPlayerEntity player) {
        this.player = player;
        this.client = client;
        loadState();
    }

    public boolean onGameTick() {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();


        if (worldSchematic == null)
            return false;


        // Start / Restart Scan
        if(Hotkeys.RESET.getKeybind().isPressed()) {
            // Reset
            logger.info("Reset.");
            scanComplete = false;
            scanning = false;
        }


        // Toggle
        if (!Configs.PRINT_MODE.getBooleanValue())
            return false;

        // Start or continue scanning if not complete
        if (!scanComplete) {
            if (!scanning) {
                startReachablePositionsScan();
                return false;
            }
            scanReachablePositionsTick();
            return false;
        }
        if(!fillEmptyList.isEmpty()){
            // Fill next
            fillEmptyBlocks();
            return true;
        }
        PlayerAbilities abilities = player.getAbilities();
        if (!abilities.allowModifyWorld)
            return false;

        if(!regions.isEmpty() && currentRegionIndex != -1 && currentRegionIndex < regions.size()){
            List<BlockPos> blocksInRegion = regions.get(currentRegionIndex).positions;
            var currentRegionName = regions.get(currentRegionIndex).getName();
            for(var i = 0; i < Configs.PLACE_BLOCKS_PER_TICK.getIntegerValue(); i++){
                if(currentBlockIndex >= blocksInRegion.size()){
                    // Set next region
                    currentRegionIndex++;
                    currentBlockIndex = 0;
                    break;
                }
                BlockPos position = blocksInRegion.get(currentBlockIndex++);
                place(position);
                totalBlocksPlaced++;
            }
            player.sendMessage(
                    net.minecraft.text.Text.literal("Placing schematic... "+ totalBlocksPlaced + " out of "+blocksFound + " blocks (current region: "+currentRegionName+")"),
                    true
            );
            // Check how many blocks have been processed since last save
            var _now = new Date().getTime();
            if(_now - lastSaveTime >= Configs.SAVE_INTERVAL.getIntegerValue()){
                // Save
                saveState();
                lastSaveTime = _now;
            }
            return true;
        }

        // If completed
        if(currentRegionIndex >= regions.size()){
            currentRegionIndex = -1;
            regions.clear();
            blocksFound = 0;
            currentBlockIndex = 0;
            currentRegionIndex = -1;
            currentScanRegionIndex = 0;
            totalBlocksScanned = 0;
            totalBlocksToScan = 0;
            totalBlocksPlaced = 0;
            clearState();
        }

        return false;
    }

    private void place(BlockPos pos){
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        BlockState stateSchematic = world.getBlockState(pos);
        BlockState stateClient = client.world.getBlockState(pos);
        
        // Skip if already matches
        if (stateClient.equals(stateSchematic)) {
            return;
        }
        
        if (stateSchematic.isAir()) {
            return;
        }


        placeBlockWithCommand(pos, stateSchematic);
    }

    private void placeBlockWithCommand(BlockPos pos, BlockState targetState) {
        if (!client.isIntegratedServerRunning()) {
            logger.warn("Command-based placement only works in single-player!");
            return;
        }
        
        // Convert BlockState to command string
        String blockStateString = blockStateToString(targetState);
        String command = String.format("setblock %d %d %d %s", pos.getX(), pos.getY(), pos.getZ(), blockStateString);
        
        // Send command
        player.networkHandler.sendChatCommand(command);
        logger.info("Executed command: /{}", command);
    }

    private String blockStateToString(BlockState state) {
        // Get the block's registry name
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        String blockName = blockId.toString();
        // Build properties string
        StringBuilder properties = new StringBuilder();
        for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
            if (properties.length() > 0) {
                properties.append(",");
            }
            properties.append(property.getName()).append("=").append(state.get(property).toString());
        }

        // If replace dirt with grass is set
        if(blockName.equals("minecraft:dirt") && Configs.REPLACE_DIRT_WITH_GRASS.getBooleanValue()){
            // Set it to minecraft:grass
            blockName = "minecraft:grass_block";
        }
        
        if (properties.length() > 0) {
            return blockName + "[" + properties.toString().toLowerCase() + "]";
        } else {
            return blockName;
        }
    }   


    private void startReachablePositionsScan() {
        logger.info("Scanning for reachable blocks...");
        scanning = true;
        scanComplete = false;
        regions.clear();
        blocksFound = 0;
        currentBlockIndex = 0;
        currentRegionIndex = -1;
        currentScanRegionIndex = 0;
        regions = new ArrayList<>();
        totalBlocksScanned = 0;
        totalBlocksToScan = 0;
        totalBlocksPlaced = 0;
        fillEmptyList.clear();

        // Use SchematicPlacementManager and SchematicPlacement
        SchematicPlacementManager placementManager =
            DataManager.getSchematicPlacementManager();
        SchematicPlacement placement =
            placementManager.getSelectedSchematicPlacement();

        if (placement == null) {
            logger.warn("No schematic placement selected!");
            // Only reset placement scan fields
            scanPlacementCurrentPos = null;
            scanning = false; 
            scanComplete = false; 
            return;
        }


        // Get subregions

        var _regions = placement.getAllSubRegionsPlacements();
        if(_regions.size() == 0){
            logger.info("The placement has no regions. Cannot continue. ");
            scanComplete = false; 
            scanning = false; 
            scanPlacementCurrentPos = null;
            return;
        }
        var _boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.ANY);
        logger.info("Found {} regions in placement. ", _regions.size());

        for (var _sr : _regions) {
            logger.info("Region: {}", _sr.getName());
            var _box = _boxes.get(_sr.getName());
            var _region = new Region(_sr.getName(),_box.copy());
            regions.add(_region);
            totalBlocksToScan += (_region.maxX - _region.minX + 1) * (_region.maxY - _region.minY + 1) * (_region.maxZ - _region.minZ + 1);
        }

        // Sort regions
        regions.sort(new Comparator<Region>() {
            private Pattern numberPattern = Pattern.compile("\\d+");

            @Override
            public int compare(Region o1, Region o2) {
                Integer num1 = extractNumber(o1.getName());
                Integer num2 = extractNumber(o2.getName());

                // If both have no numbers, maintain original order
                if (num1 == null && num2 == null) {
                    return 0;
                }

                // If only o1 has no number, it comes first
                if (num1 == null) {
                    return -1;
                }

                // If only o2 has no number, it comes first
                if (num2 == null) {
                    return 1;
                }

                // Both have numbers, compare them
                return num1.compareTo(num2);
            }

            private Integer extractNumber(String name) {
                Matcher matcher = numberPattern.matcher(name);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group());
                }
                return null;
            }
        });

        // Set currentScanRegion to first region in list
        currentScanRegionIndex = 0;
        worldSchematic = SchematicWorldHandler.getSchematicWorld();
        world = player.getWorld();
        logger.info("Total blocks to scan: {}", totalBlocksToScan);
        logger.info("Scanning will begin in next tick. Wait.");


        // Prepare for incremental scan
        scanPlacementCurrentPos = null;
    }

    private void fillEmptyBlocks(){
        if(fillEmptyList.isEmpty()){
            saveState();
            return;
        }
        for(var i = 0; i < Configs.PLACE_BLOCKS_PER_TICK.getIntegerValue(); i++){
            BlockPos pos = fillEmptyList.getLast();
            String command = String.format("setblock %d %d %d %s", pos.getX(), pos.getY(), pos.getZ(), "minecraft:air replace");
            player.networkHandler.sendChatCommand(command);
            fillEmptyList.removeLast();
            if(fillEmptyList.isEmpty()){break;}
        }
        String progressMsg = String.format(
                "Filling empty blocks: Remaining: %d blocks.",
                fillEmptyList.size()
        );
        player.sendMessage(
                net.minecraft.text.Text.literal(progressMsg),
                true
        );
    }
    
    private void scanReachablePositionsTick() {
        var currentScanRegion = regions.get(currentScanRegionIndex);
        int blocksScanned = 0;

        // Initialize scan position if needed
        if (scanPlacementCurrentPos == null) {
            scanPlacementCurrentPos = new BlockPos(currentScanRegion.minX, currentScanRegion.minY, currentScanRegion.minZ);
        }

        // Scan positions within the box
        while (blocksScanned < Configs.SCAN_BLOCKS_PER_TICK.getIntegerValue() && scanPlacementCurrentPos != null) {
            BlockPos pos = scanPlacementCurrentPos;


            SchematicBlockState state = new SchematicBlockState(player.getWorld(), worldSchematic, pos);
            if (!state.targetState.isAir())
            {
                // Add to current region
                regions.get(currentScanRegionIndex).addPosition(pos);
                blocksFound += 1;
            }
            // If local block state is NOT air, and FILL_EMPTY is toggled, we check if current Y is within range of fill empty parameter
            if(!state.currentState.isAir() && Configs.FILL_EMPTY.getBooleanValue()){
                var _maxY = Configs.FILL_HEIGHT.getIntegerValue();
                if(pos.getY() <= _maxY){
                    fillEmptyList.add(pos);
                }
            }
            blocksScanned++;

            // Increment position (x, then z, then y)
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x <= currentScanRegion.maxX) {
                scanPlacementCurrentPos = new BlockPos(x + 1, y, z);
            } else if (z <= currentScanRegion.maxZ) {
                scanPlacementCurrentPos = new BlockPos(currentScanRegion.minX, y, z + 1);
            } else if (y <= currentScanRegion.maxY) {
                scanPlacementCurrentPos = new BlockPos(currentScanRegion.minX, y + 1, currentScanRegion.minZ);
            } else {
                scanPlacementCurrentPos = null; // Finished this region
            }
        }
        totalBlocksScanned += blocksScanned;

        // Print progress to chat and log

        double percent = totalBlocksToScan > 0 ? (double) totalBlocksScanned / totalBlocksToScan * 100 : 0;
        String progressMsg = String.format(
            "Scanning region: %s... Found %d blocks so far. Total Progress: %.2f%%", currentScanRegion.getName(),
            blocksFound, 
            percent
        );
        player.sendMessage(
            net.minecraft.text.Text.literal(progressMsg),
            true
        );
        // If finished all positions in region
        if (scanPlacementCurrentPos == null) {
            if(currentScanRegionIndex < regions.size() - 1){
                currentScanRegionIndex++;
                saveState();
            }else {
                // Finished all regions
                currentRegionIndex = 0;
                scanComplete = true;
                scanning = false;
                logger.info("Scan complete. Found {} blocks within placement.", blocksFound);
                player.sendMessage(
                        net.minecraft.text.Text.literal("Scan complete. Found " + blocksFound + " blocks."),
                        false
                );
                saveState();
            }
        }else{
            // Check how many blocks have been processed since last save
            var _now = new Date().getTime();
            if(_now - lastSaveTime >= Configs.SAVE_INTERVAL.getIntegerValue()){
                // Save
                saveState();
                lastSaveTime = _now;
            }
        }
    }

    public static String getModVersionString(String modId)
    {
        for (net.fabricmc.loader.api.ModContainer container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods())
        {
            if (container.getMetadata().getId().equals(modId))
            {
                return container.getMetadata().getVersion().getFriendlyString();
            }
        }

        return "?";
    }

    public static void printDebug(String key, Object... args)
    {
        logger.info(key, args);
    }

    private void saveState(){
        var obj = new JsonObject();
        obj.addProperty("currentScanRegionIndex", currentScanRegionIndex);
        obj.addProperty("currentRegionIndex", currentRegionIndex);
        obj.addProperty("scanComplete", scanComplete);
        obj.addProperty("scanning", scanning);
        obj.addProperty("totalBlocksScanned", totalBlocksScanned);
        obj.addProperty("totalBlocksToScan", totalBlocksToScan);
        obj.addProperty("blocksFound", blocksFound);
        obj.addProperty("currentBlockIndex", currentBlockIndex);
        obj.addProperty("totalBlocksPlaced", totalBlocksPlaced);
        if(scanPlacementCurrentPos != null){
            obj.add("scanPlacementCurrentPos", JsonUtils.blockPosToJson(scanPlacementCurrentPos));
        }
        JsonArray regionsArray = new JsonArray();
        for(Region _region : regions){
            var _rJson = _region.toJson();
            regionsArray.add(_rJson);
        }
        obj.add("regions", regionsArray);

        JsonArray fillEmptyArray = new JsonArray();
        for(BlockPos _pos : fillEmptyList){
            var _posObj = new JsonObject();
            _posObj.add("pos", JsonUtils.blockPosToJson(_pos));
            fillEmptyArray.add(_posObj);
        }
        obj.add("fillEmpty", fillEmptyArray);
        JsonUtils.writeJsonToFile(obj, new java.io.File("scanner_state.json"));
    }

    private void clearState(){
        if(!Paths.get("scanner_state.json").toFile().exists() || !Paths.get("scanner_state.json").toFile().isFile()){
            return;
        }
        Paths.get("scanner_state.json").toFile().delete();
    }

    private void loadState() {
        try {
            if(!Paths.get("scanner_state.json").toFile().exists() || !Paths.get("scanner_state.json").toFile().isFile()){
                return;
            }

            worldSchematic = SchematicWorldHandler.getSchematicWorld();
            world = player.getWorld();
            String json = new String(Files.readAllBytes(Paths.get("scanner_state.json")));
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            currentScanRegionIndex = obj.get("currentScanRegionIndex").getAsInt();
            currentRegionIndex = obj.get("currentRegionIndex").getAsInt();
            scanComplete = obj.get("scanComplete").getAsBoolean();
            scanning = obj.get("scanning").getAsBoolean();
            totalBlocksScanned = obj.get("totalBlocksScanned").getAsInt();
            totalBlocksToScan = obj.get("totalBlocksToScan").getAsInt();
            blocksFound = obj.get("blocksFound").getAsInt();
            currentBlockIndex = obj.get("currentBlockIndex").getAsInt();
            totalBlocksPlaced = obj.get("totalBlocksPlaced").getAsInt();

            if (obj.has("scanPlacementCurrentPos")) {
                scanPlacementCurrentPos = JsonUtils.blockPosFromJson(obj, "scanPlacementCurrentPos");
            } else {
                scanPlacementCurrentPos = null;
            }

            regions.clear();
            JsonArray regionsArray = obj.getAsJsonArray("regions");
            for (JsonElement regionElement : regionsArray) {
                Region region = Region.fromJson(regionElement.getAsJsonObject());
                if (region != null) {
                    regions.add(region);
                }
            }

            fillEmptyList.clear();
            JsonArray fillEmptyArray = obj.getAsJsonArray("fillEmpty");
            for (JsonElement fillEmptyElem : fillEmptyArray) {
                var _pos = JsonUtils.blockPosFromJson(fillEmptyElem.getAsJsonObject(), "pos");
                fillEmptyList.add(_pos);
            }
        } catch (Exception e) {
            // Handle file not found or parsing errors
            System.out.println("Could not load state: " + e.getMessage());
        }
    }
}

