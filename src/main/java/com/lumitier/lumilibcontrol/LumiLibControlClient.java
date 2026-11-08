package com.lumitier.lumilibcontrol;

import com.lumitier.lumilib.api.LumiCatalog;
import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import com.lumitier.lumilib.api.SettingsPage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LumiLibControl: das Verwaltungsmenue fuer LumiLibControlPlugin.
 *
 * ARBEITSTEILUNG: LumiLib empfaengt die Regeln des Servers und schaltet die
 * Mods ab - das tut es fuer jeden Spieler. Diese Mod ist nur das Werkzeug
 * fuer die, die die Regeln aendern duerfen: ein Eintrag im Hub mit einem
 * Schalter pro Mod. Umlegen schickt eine Bitte an den Server; der prueft
 * die Berechtigung SELBST, speichert und verteilt an alle.
 *
 * DER SCHALTER ZEIGT IMMER DEN STAND DES SERVERS, nicht den Wunsch. Er
 * springt erst um, wenn die Antwort da ist. Sonst stuende ein Schalter auf
 * "aus", den der Server abgelehnt hat.
 */
public final class LumiLibControlClient implements ClientModInitializer {

	public static final String MOD_ID = "lumilibcontrol";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Muss mit SET_CHANNEL im Plugin uebereinstimmen. */
	public static final Identifier SET_CHANNEL = Identifier.of("lumilib", "control_set");

	/** Fassung des Formats, wie im Plugin. */
	private static final int PROTOCOL = 1;
	private static final int MAX_ID = 64;

	/** Bitte an den Server, eine Mod zu erlauben oder abzuschalten. */
	public record SetPayload(String modId, boolean allow) implements CustomPayload {

		public static final CustomPayload.Id<SetPayload> ID = new CustomPayload.Id<>(SET_CHANNEL);

		public static final PacketCodec<PacketByteBuf, SetPayload> CODEC =
			CustomPayload.codecOf(SetPayload::write, SetPayload::read);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}

		private void write(PacketByteBuf buf) {
			buf.writeVarInt(PROTOCOL);
			buf.writeString(this.modId, MAX_ID);
			buf.writeBoolean(this.allow);
		}

		/** Nur der Vollstaendigkeit halber - der Client empfaengt das nie. */
		private static SetPayload read(PacketByteBuf buf) {
			buf.readVarInt();
			return new SetPayload(buf.readString(MAX_ID), buf.readBoolean());
		}
	}

	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.playC2S().register(SetPayload.ID, SetPayload.CODEC);

		ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
		String version = container == null ? ""
			: container.getMetadata().getVersion().getFriendlyString();
		String summary = container == null ? ""
			: container.getMetadata().getDescription();

		LumiLibApi.register(new LumiMod(
			MOD_ID,
			Text.literal("LumiLibControl"),
			version,
			Text.literal(summary),
			// Icon liest LumiLib aus dem Jar.
			null,
			// Kein eigener Bildschirm - alles steht im Hub.
			null,
			LumiLibControlClient::page));
	}

	/**
	 * Die Seite im Hub - je nach Lage eine von drei.
	 *
	 * Der Hub baut sie neu, sobald der Server neue Regeln schickt. Wer also
	 * die Seite offen hat, waehrend ein anderer Admin etwas aendert, sieht
	 * es sofort.
	 */
	private static SettingsPage page() {
		SettingsPage.Builder b = SettingsPage.builder(MOD_ID);

		// 1. Kein Plugin auf dem Server, Einzelspieler oder Hauptmenue.
		//    Massgeblich ist allein, ob Regeln vom Plugin angekommen sind.
		//    NICHT canSend(): Das weiss nur, welche Kanaele der Server beim
		//    Betreten gemeldet hat - wird das Plugin danach (neu) geladen,
		//    stuende hier "kein Plugin", obwohl es laeuft.
		if (!LumiLibApi.isServerControlled()) {
			b.section("status").note("not_controlled");
			return b.build();
		}

		Map<String, Boolean> rules = LumiLibApi.serverRules();

		// 2. Plugin da, aber keine Berechtigung: nur ansehen.
		if (!LumiLibApi.canControlServer()) {
			b.section("status").warning("no_permission");
			b.section("mods");
			for (Map.Entry<String, Boolean> e : rules.entrySet()) {
				boolean on = e.getValue();
				b.row(new SettingsPage.Note(
					Text.literal(name(e.getKey())),
					Text.translatable(on ? "lumilibcontrol.state.allowed" : "lumilibcontrol.state.disabled"),
					!on));
			}
			return b.build();
		}

		// 3. Admin: ein Schalter pro Mod.
		b.section("mods");
		for (String id : rules.keySet()) {
			b.row(new SettingsPage.Toggle(
				Text.literal(name(id)),
				null,
				// Immer der Stand des Servers - siehe Klassenkommentar.
				() -> LumiLibApi.serverRules().getOrDefault(id, true),
				allow -> send(id, allow)));
		}
		b.note("applies_to_everyone");
		return b.build();
	}

	private static void send(String modId, boolean allow) {
		try {
			ClientPlayNetworking.send(new SetPayload(modId, allow));
		} catch (IllegalStateException e) {
			// Gerade nicht verbunden - dann gibt es auch nichts zu aendern.
			LOGGER.debug("[LumiLibControl] Not connected, change not sent.");
		}
	}

	/** Anzeigename aus LumiLibs Katalog, sonst die Kennung. */
	private static String name(String modId) {
		for (LumiCatalog.Entry e : LumiCatalog.KNOWN) {
			if (e.modId().equals(modId)) {
				return e.name();
			}
		}
		return modId;
	}
}
