package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Spaceship designs: every ship is a pile of stretched vanilla blocks, z is the way the nose points. */
final class Models {
	private Models() {}

	/** One box of a ship: block, center (x left, y up, z forward), size, rotation in degrees around its own center. */
	record Part(Block block, float x, float y, float z, float sx, float sy, float sz, float ry, float rx, float rz, boolean glow) {}

	/** Everything the game needs to build and fly one kind of ship. */
	static final class Spec {
		String name;
		final List<Part> parts = new ArrayList<>();
		final List<Vec3> engines = new ArrayList<>();
		final List<Vec3> guns = new ArrayList<>();
		Vec3 cockpit = new Vec3(0, 0.6, 1.0);
		float hitbox = 4f;       // scale of the invisible phantom that takes the hits (phantom is 0.9 x 0.5 blocks)
		double hp = 20, speed = 0.4, boltDamage = 3;
		int fireEvery = 60;
		double orbit = 16, height = 8;
		float pilotScale = 0.5f;
		float scale = 1f;

		Spec add(Block b, double x, double y, double z, double sx, double sy, double sz) { return add(b, x, y, z, sx, sy, sz, 0, 0, 0, false); }
		Spec glow(Block b, double x, double y, double z, double sx, double sy, double sz) { return add(b, x, y, z, sx, sy, sz, 0, 0, 0, true); }
		Spec add(Block b, double x, double y, double z, double sx, double sy, double sz, double ry, double rx, double rz, boolean glow) {
			parts.add(new Part(b, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, (float) ry, (float) rx, (float) rz, glow));
			return this;
		}
		/** Same box on both sides (x and -x), rotations mirrored. */
		Spec mir(Block b, double x, double y, double z, double sx, double sy, double sz, double ry, double rx, double rz, boolean glow) {
			add(b, x, y, z, sx, sy, sz, ry, rx, rz, glow);
			return add(b, -x, y, z, sx, sy, sz, -ry, rx, -rz, glow);
		}
		Spec mir(Block b, double x, double y, double z, double sx, double sy, double sz) { return mir(b, x, y, z, sx, sy, sz, 0, 0, 0, false); }
		Spec mirGlow(Block b, double x, double y, double z, double sx, double sy, double sz) { return mir(b, x, y, z, sx, sy, sz, 0, 0, 0, true); }
		Spec engine(double x, double y, double z) { engines.add(new Vec3(x, y, z)); return this; }
		Spec gun(double x, double y, double z) { guns.add(new Vec3(x, y, z)); return this; }

		/** A copy with every size and position multiplied by k. */
		Spec scaled(String newName, float k) {
			Spec s = new Spec();
			s.name = newName; s.scale = scale * k;
			for (Part p : parts) s.parts.add(new Part(p.block, p.x * k, p.y * k, p.z * k, p.sx * k, p.sy * k, p.sz * k, p.ry, p.rx, p.rz, p.glow));
			for (Vec3 v : engines) s.engines.add(v.scale(k));
			for (Vec3 v : guns) s.guns.add(v.scale(k));
			s.cockpit = cockpit.scale(k);
			s.hitbox = hitbox * k; s.hp = hp; s.speed = speed; s.boltDamage = boltDamage; s.fireEvery = fireEvery;
			s.orbit = orbit; s.height = height; s.pilotScale = pilotScale * k;
			return s;
		}
	}

	/** The pilot's ship: Hai-style white and teal shuttle, big enough to carry the player on top of a happy ghast. */
	static Spec playerShuttle() {
		Spec s = new Spec();
		s.name = "Shuttle";
		s.add(Blocks.QUARTZ_BLOCK, 0, -0.2, 0, 2.6, 1.5, 6.0);
		s.add(Blocks.SMOOTH_QUARTZ, 0, -0.3, 3.5, 1.8, 1.1, 1.2);
		s.add(Blocks.CONCRETE.lightBlue(), 0, -0.4, 4.3, 1.0, 0.7, 0.7);
		s.add(Blocks.CONCRETE.lightGray(), 0, -1.05, -0.3, 2.0, 0.3, 5.0);
		s.add(Blocks.CONCRETE.cyan(), 0, 0.58, -0.6, 0.5, 0.12, 4.2);
		s.mir(Blocks.CONCRETE.lightBlue(), 2.9, -0.5, -0.9, 3.2, 0.3, 2.6, 18, 0, 0, false);
		s.mir(Blocks.CONCRETE.white(), 4.6, -0.5, -1.6, 1.0, 0.3, 1.8, 18, 0, 0, false);
		s.mir(Blocks.CONCRETE.cyan(), 2.9, -0.33, -0.9, 3.0, 0.05, 0.5, 18, 0, 0, false);
		s.mir(Blocks.CONCRETE.white(), 1.45, 0.75, -3.2, 0.25, 1.4, 1.3, 0, 0, -8, false);
		s.mir(Blocks.CONCRETE.lightBlue(), 1.62, 1.55, -3.4, 0.2, 0.4, 0.7, 0, 0, -8, false);
		s.add(Blocks.STAINED_GLASS.lightBlue(), 0, 1.55, -2.4, 2.0, 1.7, 2.4);
		s.add(Blocks.CONCRETE.gray(), 0, 0.95, -0.9, 1.4, 0.2, 0.9);
		s.mir(Blocks.CONCRETE.gray(), 0.9, -0.3, -3.3, 0.9, 0.9, 0.9);
		s.add(Blocks.CONCRETE.gray(), 0, -0.3, -3.3, 0.8, 0.8, 0.9);
		s.mirGlow(Blocks.SEA_LANTERN, 0.9, -0.3, -3.85, 0.65, 0.65, 0.25);
		s.glow(Blocks.SEA_LANTERN, 0, -0.3, -3.85, 0.55, 0.55, 0.25);
		s.mirGlow(Blocks.BEACON, 4.9, -0.5, -0.9, 0.25, 0.25, 0.25);
		s.engine(0.9, -0.3, -4.0).engine(-0.9, -0.3, -4.0).engine(0, -0.3, -4.0);
		s.gun(1.6, -0.4, 3.0).gun(-1.6, -0.4, 3.0);
		s.hitbox = 4f; s.hp = 40; s.speed = 0.5; s.boltDamage = 5; s.fireEvery = 28;
		s.cockpit = new Vec3(0, 1.0, 0.2);
		return s;
	}

	/** Small allied escort: the player's shuttle, shrunk, with a mob at the controls. */
	static Spec wingman() {
		Spec s = playerShuttle().scaled("Escort Shuttle", 0.55f);
		s.hitbox = 2.6f; s.hp = 30; s.speed = 0.5; s.boltDamage = 2.5; s.fireEvery = 36; s.orbit = 11; s.height = 6;
		s.cockpit = new Vec3(0, 0.55, 0.1); s.pilotScale = 0.4f;
		return s;
	}

	/** Korath raider: black and red, forward swept blades, a pillager at the controls. */
	static Spec raider() {
		Spec s = new Spec();
		s.name = "Korath Raider";
		s.add(Blocks.POLISHED_BLACKSTONE, 0, 0, 0, 1.9, 1.0, 3.8);
		s.add(Blocks.NETHERITE_BLOCK, 0, 0.65, -0.3, 0.8, 0.45, 2.4);
		s.add(Blocks.RED_NETHER_BRICKS, 0, -0.05, 2.5, 1.0, 0.7, 1.5);
		s.add(Blocks.CONCRETE.red(), 0, -0.05, 3.5, 0.45, 0.45, 1.0);
		s.add(Blocks.CONCRETE.red(), 0, 0.1, 4.2, 0.2, 0.2, 0.6);
		s.mir(Blocks.POLISHED_BLACKSTONE, 2.0, -0.1, 0.3, 2.4, 0.22, 1.3, -28, 0, 0, false);
		s.mir(Blocks.CONCRETE.red(), 3.5, -0.1, 1.2, 1.9, 0.2, 0.4, -48, 0, 0, false);
		s.mir(Blocks.NETHERITE_BLOCK, 1.0, 0.6, -1.6, 0.2, 1.2, 1.3, 0, 0, 16, false);
		s.mir(Blocks.CONCRETE.red(), 1.15, 1.2, -1.9, 0.12, 0.45, 0.7, 0, 0, 16, false);
		s.add(Blocks.STAINED_GLASS.red(), 0, 0.6, 1.1, 1.0, 0.55, 1.2);
		s.mir(Blocks.CRYING_OBSIDIAN, 0.65, 0, -2.2, 0.75, 0.75, 0.9);
		s.mirGlow(Blocks.SHROOMLIGHT, 0.65, 0, -2.75, 0.55, 0.55, 0.25);
		s.engine(0.65, 0, -2.95).engine(-0.65, 0, -2.95);
		s.gun(3.8, -0.1, 1.9).gun(-3.8, -0.1, 1.9);
		s.hitbox = 4f; s.hp = 30; s.speed = 0.42; s.boltDamage = 3; s.fireEvery = 55;
		s.cockpit = new Vec3(0, 0.45, 1.1); s.pilotScale = 0.42f;
		s.orbit = 15; s.height = 7;
		return s;
	}

	/** Korath warlord: the raider blown up to twice its size, with the hit points to match. */
	static Spec warlord() {
		Spec s = raider().scaled("Korath Warlord", 2.4f);
		s.hp = 200; s.speed = 0.3; s.boltDamage = 4; s.fireEvery = 22; s.orbit = 26; s.height = 14; s.hitbox = 4f * 2.4f;
		return s;
	}

	/** The Alliance flagship: a slow gray cruiser that patrols the sky and cannot be sunk. */
	static Spec falcon() {
		Spec s = new Spec();
		s.name = "Falcon Cruiser";
		s.add(Blocks.CONCRETE.lightGray(), 0, 0, 0, 8, 3, 22);
		s.add(Blocks.CONCRETE.white(), 0, 2.0, -2, 5.5, 1.4, 12);
		s.add(Blocks.CONCRETE.gray(), 0, 3.6, -6, 2.6, 2.2, 3.2);
		s.add(Blocks.STAINED_GLASS.lightBlue(), 0, 4.2, -4.3, 2.2, 0.8, 0.3);
		s.add(Blocks.CONCRETE.blue(), 0, 2.75, 3.5, 3, 0.12, 8);
		s.add(Blocks.IRON_BLOCK, 0, -0.3, 12.5, 4.6, 2.2, 4);
		s.add(Blocks.IRON_BLOCK, 0, -0.5, 15, 2.2, 1.2, 3);
		s.add(Blocks.CONCRETE.cyan(), 0, -0.5, 17, 0.8, 0.7, 1);
		s.mir(Blocks.CONCRETE.gray(), 5.6, -0.6, -3, 3.2, 2.2, 13);
		s.mir(Blocks.CONCRETE.lightBlue(), 5.6, 0.55, -3, 3.3, 0.12, 13);
		s.mir(Blocks.CONCRETE.black(), 5.6, 1.2, 2, 1.4, 0.9, 1.8);
		s.mir(Blocks.IRON_BLOCK, 5.6, 1.3, 4, 0.3, 0.3, 2.6);
		s.mir(Blocks.CONCRETE.black(), 2, 3.0, 7, 1.2, 0.8, 1.6);
		s.mir(Blocks.IRON_BLOCK, 2, 3.1, 8.8, 0.3, 0.3, 2.4);
		s.mir(Blocks.CONCRETE.gray(), 6.2, -1.5, -10.5, 2.4, 2.4, 3);
		s.mirGlow(Blocks.SEA_LANTERN, 6.2, -1.5, -12.2, 1.8, 1.8, 0.5);
		s.mir(Blocks.CONCRETE.gray(), 2.3, 0.2, -11.5, 2.2, 2.2, 2);
		s.mirGlow(Blocks.SEA_LANTERN, 2.3, 0.2, -12.8, 1.7, 1.7, 0.5);
		s.mirGlow(Blocks.REDSTONE_LAMP, 3.9, 0.3, 11, 0.4, 0.4, 0.4);
		s.mirGlow(Blocks.STAINED_GLASS.red(), 4.1, 1.55, -9, 0.4, 0.4, 0.4);
		s.engine(6.2, -1.5, -12.6).engine(-6.2, -1.5, -12.6).engine(2.3, 0.2, -13.4).engine(-2.3, 0.2, -13.4);
		s.gun(5.6, 1.3, 5.5).gun(-5.6, 1.3, 5.5).gun(2, 3.1, 10).gun(-2, 3.1, 10);
		s.hitbox = 20f; s.hp = 1000; s.speed = 0.22; s.boltDamage = 5; s.fireEvery = 16;
		s.orbit = 42; s.height = 24;
		return s;
	}
}
