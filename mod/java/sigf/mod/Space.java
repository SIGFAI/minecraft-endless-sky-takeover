package sigf.mod;

import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Planets for the sky: voxel spheres made of a few hundred stretched vanilla blocks, with fullbright faces. */
final class Space {
	private Space() {}

	record Voxel(int x, int y, int z) {}

	/** A sphere of radius r cells (cell blocks wide) around c; rows along x with the same block become one box. */
	static int sphere(Vec3 c, int r, float cell, Function<Voxel, Block> paint) {
		int boxes = 0;
		for (int iy = -r; iy <= r; iy++) {
			for (int iz = -r; iz <= r; iz++) {
				int ix = -r;
				while (ix <= r) {
					if (!inside(ix, iy, iz, r)) { ix++; continue; }
					Block b = paint.apply(new Voxel(ix, iy, iz));
					int end = ix;
					while (end + 1 <= r && inside(end + 1, iy, iz, r) && paint.apply(new Voxel(end + 1, iy, iz)) == b) end++;
					float len = (end - ix + 1) * cell;
					double cx = c.x + (ix + end) * 0.5 * cell, cy = c.y + iy * cell, cz = c.z + iz * cell;
					Fleet.box(b, new Vec3(cx, cy, cz), new Vector3f(len, cell, cell), new Quaternionf(), true, 6f);
					boxes++;
					ix = end + 1;
				}
			}
		}
		return boxes;
	}

	static boolean inside(int x, int y, int z, int r) { return x * x + y * y + z * z <= r * r + r * 0.6; }

	static final Block[] BANDS = {
		Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.orange(),
		Blocks.DYED_TERRACOTTA.brown(), Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.CONCRETE.orange(),
		Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.yellow()
	};

	/** Banded gas giant with a tilted double ring. */
	static void gasGiant(Vec3 c, int r, float cell) {
		sphere(c, r, cell, v -> {
			int band = v.y() + r + (int) Math.round(Math.sin(v.x() * 0.8 + v.z() * 0.5) * 0.6);
			return BANDS[Math.floorMod(band, BANDS.length)];
		});
		Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(22)).rotateX((float) Math.toRadians(-12));
		int segs = 28;
		for (int ring = 0; ring < 2; ring++) {
			double rad = (r + 3.2 + ring * 2.6) * cell;
			float width = cell * (ring == 0 ? 1.8f : 1.2f);
			float len = (float) (2 * Math.PI * rad / segs) * 1.08f;
			for (int i = 0; i < segs; i++) {
				double a = i * 2 * Math.PI / segs;
				Vector3f off = new Vector3f((float) (Math.cos(a) * rad), 0, (float) (Math.sin(a) * rad));
				tilt.transform(off);
				Quaternionf q = new Quaternionf(tilt).rotateY((float) -a);
				Block b = (i + ring) % 5 == 0 ? Blocks.CONCRETE.lightGray() : ring == 0 ? Blocks.DYED_TERRACOTTA.white() : Blocks.DYED_TERRACOTTA.orange();
				if ((i * 7 + ring) % 11 == 0) continue;
				Fleet.box(b, c.add(off.x, off.y, off.z), new Vector3f(len, cell * 0.12f, width), q, true, 6f);
			}
		}
	}

	/** A blue ocean world with green continents and white poles. */
	static void oceanWorld(Vec3 c, int r, float cell) {
		sphere(c, r, cell, v -> {
			if (Math.abs(v.y()) >= r) return Blocks.CONCRETE.white();
			double n = Math.sin(v.x() * 1.1 + v.y() * 0.7) + Math.sin(v.z() * 1.3 - v.x() * 0.6) + Math.cos(v.y() * 1.7 + v.z() * 0.9);
			if (n > 1.2) return Blocks.CONCRETE.lime();
			if (n > 0.8) return Blocks.CONCRETE.green();
			if (n < -1.6) return Blocks.STAINED_GLASS.white();
			return Blocks.CONCRETE.blue();
		});
	}

	/** A small gray moon. */
	static void moon(Vec3 c, int r, float cell) {
		sphere(c, r, cell, v -> ((v.x() * 7 + v.y() * 13 + v.z() * 5) & 3) == 0 ? Blocks.CONCRETE.gray() : Blocks.CONCRETE.lightGray());
	}
}
