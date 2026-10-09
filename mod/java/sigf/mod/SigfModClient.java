package sigf.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/** Boarding a shuttle switches to the chase camera so the pilot sees the ship; leaving it puts the view back. */
public final class SigfModClient implements ClientModInitializer {
	private static boolean flying;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(SigfModClient::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null) { flying = false; return; }
		boolean now = mc.player.getVehicle() instanceof HappyGhast;
		if (now && !flying) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (!now && flying) mc.options.setCameraType(CameraType.FIRST_PERSON);
		flying = now;
	}
}
