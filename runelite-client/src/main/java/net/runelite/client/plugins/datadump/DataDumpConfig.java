/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.datadump;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.datadump.npc.RenderStyle;

import java.awt.*;

@ConfigGroup("datadump")
public interface DataDumpConfig extends Config
{
	@ConfigItem(
		position = 0,
		keyName = "dumpSpawnData",
		name = "Dump NPC spawn data",
		description = "Configures whether or not NPC data is dumped to npc-spawns.json"
	)
	default boolean dumpSpawnData()
	{
		return false;
	}

	@ConfigItem(
			position = 1,
			keyName = "dumpItemData",
			name = "Dump item data",
			description = "Configures whether or not item data is dumped to game-items.json"
	)
	default boolean dumpItemData()
	{
		return false;
	}

	@ConfigItem(
			position = 2,
			keyName = "dumpObjectData",
			name = "Dump object data",
			description = "Configures whether or not object data is dumped to game-objects.json"
	)
	default boolean dumpObjectData()
	{
		return false;
	}

	@ConfigItem(
		position = 3,
		keyName = "highlightStyle",
		name = "Highlight Style",
		description = "Highlight setting"
	)
	default RenderStyle renderStyle()
	{
		return RenderStyle.HULL;
	}

	@ConfigItem(
		position = 4,
		keyName = "npcColor",
		name = "Highlight Color",
		description = "Color of the NPC highlight"
	)
	default Color getHighlightColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		position = 5,
		keyName = "drawNames",
		name = "Draw names above NPC",
		description = "Configures whether or not NPC names should be drawn above the NPC"
	)
	default boolean drawNames()
	{
		return false;
	}

	@ConfigItem(
		position = 6,
		keyName = "drawMinimapNames",
		name = "Draw names on minimap",
		description = "Configures whether or not NPC names should be drawn on the minimap"
	)
	default boolean drawMinimapNames()
	{
		return false;
	}

	@ConfigItem(
		position = 7,
		keyName = "highlightObjects",
		name = "Highlight objects",
		description = "Highlight tracked objects"
	)
	default boolean highlightObjects()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		position = 8,
		keyName = "objectMarkerColor",
		name = "Object Marker color",
		description = "Configures the color of object marker"
	)
	default Color objectMarkerColor()
	{
		return Color.YELLOW;
	}


}