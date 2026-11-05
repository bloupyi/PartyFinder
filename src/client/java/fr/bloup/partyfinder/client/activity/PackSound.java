package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** A sound from a pack json file: {"id", "volume", "pitch"}; null or a missing key means silence. */
public record PackSound(Identifier id, float volume, float pitch) {
    public static PackSound parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject o = json.getAsJsonObject();
        if (!o.has("id")) return null;
        Identifier id = Identifier.tryParse(o.get("id").getAsString());
        if (id == null) return null;
        float volume = o.has("volume") ? Math.clamp(o.get("volume").getAsFloat(), 0f, 1f) : 1f;
        float pitch = o.has("pitch") ? Math.clamp(o.get("pitch").getAsFloat(), 0.5f, 2f) : 1f;
        return new PackSound(id, volume, pitch);
    }

    public void play() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), pitch, volume));
    }
}
