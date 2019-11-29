/*
 * Copyright (c) 2018, James Swindle <wilingua@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins.npchighlight;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicID;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HeadIcon;
import net.runelite.api.MenuAction;
import static net.runelite.api.MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldmap.TeleportLocationData;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;
import java.lang.reflect.Type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@PluginDescriptor(
	name = "NPC Indicators",
	description = "Highlight NPCs on-screen and/or on the minimap",
	tags = {"highlight", "minimap", "npcs", "overlay", "respawn", "tags"}
)
@Slf4j
public class NpcIndicatorsPlugin extends Plugin
{
	private static final int MAX_ACTOR_VIEW_RANGE = 15;

	// Option added to NPC menu
	private static final String TAG = "Tag";
	private static final String UNTAG = "Un-tag";

	private static final Set<MenuAction> NPC_MENU_ACTIONS = ImmutableSet.of(MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
		MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION);

	@Inject
	private Client client;

	@Inject
	private NpcIndicatorsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcSceneOverlay npcSceneOverlay;

	@Inject
	private NpcMinimapOverlay npcMinimapOverlay;

	@Inject
	private NpcIndicatorsInput inputListener;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Setter(AccessLevel.PACKAGE)
	private boolean hotKeyPressed = false;

	/**
	 * NPCs to highlight
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> highlightedNpcs = new HashSet<>();

	private final Set<NPC> cachedNpcs = new HashSet<>();

	/**
	 * Dead NPCs that should be displayed with a respawn indicator if the config is on.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Map<Integer, MemorizedNpc> deadNpcsToDisplay = new HashMap<>();

	/**
	 * The time when the last game tick event ran.
	 */
	@Getter(AccessLevel.PACKAGE)
	private Instant lastTickUpdate;

	/**
	 * Tagged NPCs that have died at some point, which are memorized to
	 * remember when and where they will respawn
	 */
	private final Map<Integer, MemorizedNpc> memorizedNpcs = new HashMap<>();

	/**
	 * Highlight strings from the configuration
	 */
	private List<String> highlights = new ArrayList<>();

	/**
	 * NPC ids marked with the Tag option
	 */
	private final Set<Integer> npcTags = new HashSet<>();

	/**
	 * Tagged NPCs that spawned this tick, which need to be verified that
	 * they actually spawned and didn't just walk into view range.
	 */
	private final List<NPC> spawnedNpcsThisTick = new ArrayList<>();

	/**
	 * Tagged NPCs that despawned this tick, which need to be verified that
	 * they actually spawned and didn't just walk into view range.
	 */
	private final List<NPC> despawnedNpcsThisTick = new ArrayList<>();

	/**
	 * World locations of graphics object which indicate that an
	 * NPC teleported that were played this tick.
	 */
	private final Set<WorldPoint> teleportGraphicsObjectSpawnedThisTick = new HashSet<>();

	/**
	 * The players location on the last game tick.
	 */
	private WorldPoint lastPlayerLocation;

	/**
	 * When hopping worlds, NPCs can spawn without them actually respawning,
	 * so we would not want to mark it as a real spawn in those cases.
	 */
	private boolean skipNextSpawnCheck = false;

	@Provides
	NpcIndicatorsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcIndicatorsConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(npcSceneOverlay);
		overlayManager.add(npcMinimapOverlay);
		keyManager.registerKeyListener(inputListener);
		highlights = getHighlights();
		clientThread.invoke(() ->
		{
			skipNextSpawnCheck = true;
			rebuildAllNpcs();
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(npcSceneOverlay);
		overlayManager.remove(npcMinimapOverlay);
		deadNpcsToDisplay.clear();
		memorizedNpcs.clear();
		spawnedNpcsThisTick.clear();
		despawnedNpcsThisTick.clear();
		teleportGraphicsObjectSpawnedThisTick.clear();
		npcTags.clear();
		highlightedNpcs.clear();
		keyManager.unregisterKeyListener(inputListener);
	}

	String getRegionNameByRegionId(int regionId) {
		for (TeleportLocationData locationData : TeleportLocationData.values()) {
			if (locationData.getLocation().getRegionID() == regionId) {
				return locationData.getDestination();
			}
		}
		return null;
	}

	public class RemoveNullListSerializer<T> implements JsonSerializer<List<T>> {

		@Override
		public JsonElement serialize(List<T> src, Type typeOfSrc,
									 JsonSerializationContext context) {
			JsonArray result = new JsonArray();

			// remove all null values
			if (src != null) {
				src.removeAll(Collections.singleton(null));

				// create json Result
				for (T item : src) {
					result.add(context.serialize(item));
				}
			}

			return result;
		}
	}

	@AllArgsConstructor
	class Position {
		int x,y,z;
	}

	@AllArgsConstructor
	class Region {
		int id;
		String name;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Region region = (Region) o;
			return id == region.id;
		}
	}

	@Data
	@RequiredArgsConstructor
	class Npc {
		final int id;
		final String name;
		final int combat_level;
		final int health;
		final int direction;
		final Region region;
		final Position position;

		boolean intractable, visible;
		List<String> actions;
		List<Integer> configs;
		String head_icon;
	}

	@Data
	@RequiredArgsConstructor
	class RegionCategory {
		final String region;

		Map<Integer, Npc> npcs = new ConcurrentHashMap<>();

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			RegionCategory that = (RegionCategory) o;
			return Objects.equals(region, that.region);
		}
	}

	Map<Integer, RegionCategory> regions = new ConcurrentHashMap<>();
	Gson gson = new Gson().newBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(List.class, new RemoveNullListSerializer())
			.create();

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) throws IOException {
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
			event.getGameState() == GameState.HOPPING)
		{
			highlightedNpcs.clear();
			deadNpcsToDisplay.clear();
			memorizedNpcs.forEach((id, npc) -> npc.setDiedOnTick(-1));
			lastPlayerLocation = null;
			skipNextSpawnCheck = true;
		}
		else if (event.getGameState() == GameState.LOGGED_IN) {

			final File file = new File(RuneLite.RUNELITE_DIR + "/npc-spawns.json");
			if (!file.exists()) {
				file.createNewFile();
			}

			executor.scheduleWithFixedDelay(() -> {
				if (!cachedNpcs.isEmpty()) {
					Map<Integer, Npc> npcs = new HashMap<>();

					for (NPC npc : cachedNpcs) {
						InputStream is = null;
						try {
							is = new FileInputStream(file);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}

						npcs = gson.fromJson(new InputStreamReader(is), Map.class);
						if (npcs == null) {
							npcs = new HashMap<>();
						}

						int regionId = npc.getWorldLocation().getRegionID();
						String regionName = getRegionNameByRegionId(regionId);

						if (!npc.isDead() && !npcs.containsKey(npc.getIndex())) {
							Npc storableNpc = new Npc(npc.getId(),
								npc.getName(),
								npc.getCombatLevel(),
								npc.getHealth(),
								npc.getOrientation(),
								new Region(npc.getWorldLocation().getRegionID(), regionName),
								new Position(
									npc.getWorldLocation().getX(),
									npc.getWorldLocation().getY(),
									npc.getWorldLocation().getPlane())
							);

							NPCComposition npcComposition = npc.getComposition();
							if (npcComposition != null) {
								storableNpc.setIntractable(npcComposition.isInteractible());
								storableNpc.setActions(new ArrayList<>(Arrays.asList(npcComposition.getActions())));
								int[] confArray = npcComposition.getConfigs();
								if (confArray != null) {
									List<Integer> configs = new ArrayList<>(new ArrayList<>(confArray.length));
									for (int conf : confArray) {
										configs.add(conf);
									}
									storableNpc.setConfigs(configs);
								}
								storableNpc.setVisible(npcComposition.isVisible());

								HeadIcon headIcon = npcComposition.getOverheadIcon();
								if (headIcon != null) {
									storableNpc.setHead_icon(headIcon.name());
								}
							}

							npcs.put(npc.getIndex(), storableNpc);
						}

						RegionCategory regionCategory = regions.get(regionId);
						if (regionCategory == null) {
							regionCategory = new RegionCategory(regionName);
							regions.put(regionId, regionCategory);
						}

						regionCategory.npcs.putAll(npcs);
					}

					OutputStream os = null;
					try {
						os = new FileOutputStream(file);
						os.write(gson.toJson(regions).getBytes());
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
						try {
							if (os != null) {
								os.close();
							}
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			}, 5, 5, TimeUnit.SECONDS);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals("npcindicators"))
		{
			return;
		}

		highlights = getHighlights();
		rebuildAllNpcs();
	}

	@Subscribe
	public void onFocusChanged(FocusChanged focusChanged)
	{
		if (!focusChanged.isFocused())
		{
			hotKeyPressed = false;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int type = event.getType();

		if (type >= MENU_ACTION_DEPRIORITIZE_OFFSET)
		{
			type -= MENU_ACTION_DEPRIORITIZE_OFFSET;
		}

		if (config.highlightMenuNames() &&
			NPC_MENU_ACTIONS.contains(MenuAction.of(type)) &&
			highlightedNpcs.stream().anyMatch(npc -> npc.getIndex() == event.getIdentifier()))
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			final MenuEntry menuEntry = menuEntries[menuEntries.length - 1];
			final String target = ColorUtil.prependColorTag(Text.removeTags(event.getTarget()), config.getHighlightColor());
			menuEntry.setTarget(target);
			client.setMenuEntries(menuEntries);
		}
		else if (hotKeyPressed && type == MenuAction.EXAMINE_NPC.getId())
		{
			// Add tag option
			MenuEntry[] menuEntries = client.getMenuEntries();
			menuEntries = Arrays.copyOf(menuEntries, menuEntries.length + 1);
			final MenuEntry tagEntry = menuEntries[menuEntries.length - 1] = new MenuEntry();
			tagEntry.setOption(highlightedNpcs.stream().anyMatch(npc -> npc.getIndex() == event.getIdentifier()) ? UNTAG : TAG);
			tagEntry.setTarget(event.getTarget());
			tagEntry.setParam0(event.getActionParam0());
			tagEntry.setParam1(event.getActionParam1());
			tagEntry.setIdentifier(event.getIdentifier());
			tagEntry.setType(MenuAction.RUNELITE.getId());
			client.setMenuEntries(menuEntries);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked click)
	{
		if (click.getMenuAction() != MenuAction.RUNELITE ||
			!(click.getMenuOption().equals(TAG) || click.getMenuOption().equals(UNTAG)))
		{
			return;
		}

		final int id = click.getId();
		final boolean removed = npcTags.remove(id);
		final NPC[] cachedNPCs = client.getCachedNPCs();
		final NPC npc = cachedNPCs[id];

		if (npc == null || npc.getName() == null)
		{
			return;
		}

		if (removed)
		{
			highlightedNpcs.remove(npc);
			memorizedNpcs.remove(npc.getIndex());
		}
		else
		{
			memorizeNpc(npc);
			npcTags.add(id);
			highlightedNpcs.add(npc);
		}

		click.consume();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();
		final String npcName = npc.getName();

		if (npcName == null)
		{
			return;
		}

		if (npcTags.contains(npc.getIndex()))
		{
			memorizeNpc(npc);
			highlightedNpcs.add(npc);
			spawnedNpcsThisTick.add(npc);
			return;
		}

		for (String highlight : highlights)
		{
			if (WildcardMatcher.matches(highlight, npcName))
			{
				memorizeNpc(npc);
				highlightedNpcs.add(npc);
				spawnedNpcsThisTick.add(npc);
				break;
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();

		if (memorizedNpcs.containsKey(npc.getIndex()))
		{
			despawnedNpcsThisTick.add(npc);
		}

		highlightedNpcs.remove(npc);
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		final GraphicsObject go = event.getGraphicsObject();

		if (go.getId() == GraphicID.GREY_BUBBLE_TELEPORT)
		{
			teleportGraphicsObjectSpawnedThisTick.add(WorldPoint.fromLocal(client, go.getLocation()));
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		removeOldHighlightedRespawns();
		validateSpawnedNpcs();
		lastTickUpdate = Instant.now();
		lastPlayerLocation = client.getLocalPlayer().getWorldLocation();
	}

	private static boolean isInViewRange(WorldPoint wp1, WorldPoint wp2)
	{
		int distance = wp1.distanceTo(wp2);
		return distance < MAX_ACTOR_VIEW_RANGE;
	}

	private static WorldPoint getWorldLocationBehind(NPC npc)
	{
		final int orientation = npc.getOrientation() / 256;
		int dx = 0, dy = 0;

		switch (orientation)
		{
			case 0: // South
				dy = -1;
				break;
			case 1: // Southwest
				dx = -1;
				dy = -1;
				break;
			case 2: // West
				dx = -1;
				break;
			case 3: // Northwest
				dx = -1;
				dy = 1;
				break;
			case 4: // North
				dy = 1;
				break;
			case 5: // Northeast
				dx = 1;
				dy = 1;
				break;
			case 6: // East
				dx = 1;
				break;
			case 7: // Southeast
				dx = 1;
				dy = -1;
				break;
		}

		final WorldPoint currWP = npc.getWorldLocation();
		return new WorldPoint(currWP.getX() - dx, currWP.getY() - dy, currWP.getPlane());
	}

	private void memorizeNpc(NPC npc)
	{
		final int npcIndex = npc.getIndex();
		memorizedNpcs.putIfAbsent(npcIndex, new MemorizedNpc(npc));

		for (NPC savedNpc : cachedNpcs) {
			if (savedNpc.getIndex() == npcIndex) {
				return;
			}
		}
		cachedNpcs.add(npc);
	}

	private void removeOldHighlightedRespawns()
	{
		deadNpcsToDisplay.values().removeIf(x -> x.getDiedOnTick() + x.getRespawnTime() <= client.getTickCount() + 1);
	}

	@VisibleForTesting
	List<String> getHighlights()
	{
		final String configNpcs = config.getNpcToHighlight().toLowerCase();

		if (configNpcs.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configNpcs);
	}

	private void rebuildAllNpcs()
	{
		highlightedNpcs.clear();

		if (client.getGameState() != GameState.LOGGED_IN &&
			client.getGameState() != GameState.LOADING)
		{
			// NPCs are still in the client after logging out,
			// but we don't want to highlight those.
			return;
		}

		outer:
		for (NPC npc : client.getNpcs())
		{
			final String npcName = npc.getName();

			if (npcName == null)
			{
				continue;
			}

			if (npcTags.contains(npc.getIndex()))
			{
				highlightedNpcs.add(npc);
				continue;
			}

			for (String highlight : highlights)
			{
				if (WildcardMatcher.matches(highlight, npcName))
				{
					memorizeNpc(npc);
					highlightedNpcs.add(npc);
					continue outer;
				}
			}

			// NPC is not highlighted
			memorizedNpcs.remove(npc.getIndex());
		}
	}

	private void validateSpawnedNpcs()
	{
		if (skipNextSpawnCheck)
		{
			skipNextSpawnCheck = false;
		}
		else
		{
			for (NPC npc : despawnedNpcsThisTick)
			{
				if (!teleportGraphicsObjectSpawnedThisTick.isEmpty())
				{
					if (teleportGraphicsObjectSpawnedThisTick.contains(npc.getWorldLocation()))
					{
						// NPC teleported away, so we don't want to add the respawn timer
						continue;
					}
				}

				if (isInViewRange(client.getLocalPlayer().getWorldLocation(), npc.getWorldLocation()))
				{
					final MemorizedNpc mn = memorizedNpcs.get(npc.getIndex());

					if (mn != null)
					{
						mn.setDiedOnTick(client.getTickCount() + 1); // This runs before tickCounter updates, so we add 1

						if (!mn.getPossibleRespawnLocations().isEmpty())
						{
							log.debug("Starting {} tick countdown for {}", mn.getRespawnTime(), mn.getNpcName());
							deadNpcsToDisplay.put(mn.getNpcIndex(), mn);
						}
					}
				}
			}

			for (NPC npc : spawnedNpcsThisTick)
			{
				if (!teleportGraphicsObjectSpawnedThisTick.isEmpty())
				{
					if (teleportGraphicsObjectSpawnedThisTick.contains(npc.getWorldLocation()) ||
						teleportGraphicsObjectSpawnedThisTick.contains(getWorldLocationBehind(npc)))
					{
						// NPC teleported here, so we don't want to update the respawn timer
						continue;
					}
				}

				if (lastPlayerLocation != null && isInViewRange(lastPlayerLocation, npc.getWorldLocation()))
				{
					final MemorizedNpc mn = memorizedNpcs.get(npc.getIndex());

					if (mn.getDiedOnTick() != -1)
					{
						final int respawnTime = client.getTickCount() + 1 - mn.getDiedOnTick();

						// By killing a monster and leaving the area before seeing it again, an erroneously lengthy
						// respawn time can be recorded. Thus, if the respawn time is already set and is greater than
						// the observed time, assume that the lower observed respawn time is correct.
						if (mn.getRespawnTime() == -1 || respawnTime < mn.getRespawnTime())
						{
							mn.setRespawnTime(respawnTime);
						}

						mn.setDiedOnTick(-1);
					}

					final WorldPoint npcLocation = npc.getWorldLocation();

					// An NPC can move in the same tick as it spawns, so we also have
					// to consider whatever tile is behind the npc
					final WorldPoint possibleOtherNpcLocation = getWorldLocationBehind(npc);

					mn.getPossibleRespawnLocations().removeIf(x ->
						x.distanceTo(npcLocation) != 0 && x.distanceTo(possibleOtherNpcLocation) != 0);

					if (mn.getPossibleRespawnLocations().isEmpty())
					{
						mn.getPossibleRespawnLocations().add(npcLocation);
						mn.getPossibleRespawnLocations().add(possibleOtherNpcLocation);
					}
				}
			}
		}

		spawnedNpcsThisTick.clear();
		despawnedNpcsThisTick.clear();
		teleportGraphicsObjectSpawnedThisTick.clear();
	}
}
