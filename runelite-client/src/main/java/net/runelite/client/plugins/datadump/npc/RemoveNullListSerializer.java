package net.runelite.client.plugins.datadump.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

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