package net.runelite.client.plugins.datadump.npc;

import lombok.Data;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.datadump.data.Position;
import net.runelite.client.plugins.datadump.data.Region;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class DummyNPC {

    private int index;
    private Npc npc;
    private boolean dead;

    public DummyNPC(NPC npc, NPCManager npcManager) {
        this.index = npc.getIndex();
        int regionId = npc.getWorldLocation().getRegionID();
        String regionName = getRegionNameByRegionId(regionId);

        int id = npc.getId();
        String name = npc.getName();
        int combatLevel = npc.getCombatLevel();
        Integer health = npcManager.getHealth(id);
        if (health == null) {
            health = npc.getHealth();
        }

        this.npc = new Npc(id, name, combatLevel, health,
            npc.getOrientation(),
            new Region(regionId, regionName),
            new Position(
                npc.getWorldLocation().getX(),
                npc.getWorldLocation().getY(),
                npc.getWorldLocation().getPlane())
        );

        NPCComposition npcComposition = npc.getComposition();
        if (npcComposition != null) {
            this.npc.setInteractable(npcComposition.isInteractible());
            this.npc.setActions(new ArrayList<>(Arrays.asList(npcComposition.getActions())));
            int[] confArray = npcComposition.getConfigs();
            if (confArray != null) {
                List<Integer> configs = new ArrayList<>(new ArrayList<>(confArray.length));
                for (int conf : confArray) {
                    configs.add(conf);
                }
                this.npc.setConfigs(configs);
            }
            this.npc.setVisible(npcComposition.isVisible());

            HeadIcon headIcon = npcComposition.getOverheadIcon();
            if (headIcon != null) {
                this.npc.setHead_icon(headIcon.name());
            }
        }
    }

    private String getRegionNameByRegionId(int regionId) {
        for (TeleportLocationData locationData : TeleportLocationData.values()) {
            if (locationData.getLocation().getRegionID() == regionId) {
                return locationData.getDestination();
            }
        }
        return null;
    }

    public int getId() {
        return npc.getId();
    }
}
