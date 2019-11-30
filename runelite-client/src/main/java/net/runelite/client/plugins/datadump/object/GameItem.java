package net.runelite.client.plugins.datadump.object;

import lombok.Data;
import net.runelite.client.plugins.datadump.data.Position;

@Data
public class GameItem {

    int id;

    int region_id;
    String name;
    int quantity;
    Position position;

    public GameItem() {
    }

    public GameItem(int id) {
        this.id = id;
    }
}
