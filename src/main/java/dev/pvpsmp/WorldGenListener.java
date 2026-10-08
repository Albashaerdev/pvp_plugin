package dev.pvpsmp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Plugin-side world generation:
 *  - Nether: many more ancient debris veins at every height.
 *  - Overworld: extra diamond ore veins in deep dark (ancient city) chunks.
 * Applies to NEWLY generated chunks only. (Ancient-city frequency/mountains: see the bundled datapack.)
 */
public final class WorldGenListener implements Listener {
    private final PvPSMPPlugin plugin;

    public WorldGenListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) attach(w);
    }

    @EventHandler
    public void onInit(WorldInitEvent e) { attach(e.getWorld()); }

    private void attach(World w) {
        if (w.getPopulators().stream().anyMatch(p -> p instanceof Debris || p instanceof DeepDiamonds)) return;
        switch (w.getEnvironment()) {
            case NETHER -> w.getPopulators().add(new Debris(plugin));
            case NORMAL -> w.getPopulators().add(new DeepDiamonds(plugin));
            default -> { }
        }
    }

    private static boolean exposed(LimitedRegion reg, int x, int y, int z) {
        int[][] d = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            if (reg.isInRegion(nx, ny, nz)) {
                Material t = reg.getType(nx, ny, nz);
                if (t.isAir() || t == Material.LAVA) return true;
            }
        }
        return false;
    }

    static final class Debris extends BlockPopulator {
        private final PvPSMPPlugin plugin;
        Debris(PvPSMPPlugin plugin) { this.plugin = plugin; }

        @Override
        public void populate(WorldInfo info, Random r, int cx, int cz, LimitedRegion reg) {
            int veins = plugin.getConfig().getInt("worldgen.debris-veins-per-chunk", 8);
            int size = plugin.getConfig().getInt("worldgen.debris-vein-size", 3);
            for (int i = 0; i < veins; i++) {
                int x = cx * 16 + r.nextInt(16), z = cz * 16 + r.nextInt(16), y = 8 + r.nextInt(110);
                for (int n = 0; n < size; n++) {
                    int px = x + r.nextInt(3) - 1, py = y + r.nextInt(3) - 1, pz = z + r.nextInt(3) - 1;
                    if (!reg.isInRegion(px, py, pz)) continue;
                    Material m = reg.getType(px, py, pz);
                    if (m != Material.NETHERRACK && m != Material.BASALT && m != Material.BLACKSTONE) continue;
                    if (exposed(reg, px, py, pz)) continue;
                    reg.setType(px, py, pz, Material.ANCIENT_DEBRIS);
                }
            }
        }
    }

    static final class DeepDiamonds extends BlockPopulator {
        private final PvPSMPPlugin plugin;
        DeepDiamonds(PvPSMPPlugin plugin) { this.plugin = plugin; }

        @Override
        public void populate(WorldInfo info, Random r, int cx, int cz, LimitedRegion reg) {
            int bx = cx * 16 + 8, bz = cz * 16 + 8;
            if (!reg.isInRegion(bx, -40, bz) || !Biome.DEEP_DARK.equals(reg.getBiome(bx, -40, bz))) return;
            int veins = plugin.getConfig().getInt("worldgen.deep-dark-diamond-veins-per-chunk", 6);
            for (int i = 0; i < veins; i++) {
                int x = cx * 16 + r.nextInt(16), z = cz * 16 + r.nextInt(16), y = -58 + r.nextInt(40);
                for (int n = 0; n < 5; n++) {
                    int px = x + r.nextInt(3) - 1, py = y + r.nextInt(3) - 1, pz = z + r.nextInt(3) - 1;
                    if (!reg.isInRegion(px, py, pz)) continue;
                    if (reg.getType(px, py, pz) == Material.DEEPSLATE) reg.setType(px, py, pz, Material.DEEPSLATE_DIAMOND_ORE);
                }
            }
        }
    }
}
