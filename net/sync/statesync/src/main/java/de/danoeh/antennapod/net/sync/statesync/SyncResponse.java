package de.danoeh.antennapod.net.sync.statesync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public record SyncResponse(long acceptedLocalSequence, long nextCursor, List<SyncChange> changes) {
    public SyncResponse {
        if (acceptedLocalSequence < 0 || nextCursor < 0) {
            throw new IllegalArgumentException("Invalid StateSync response sequence");
        }
        changes = List.copyOf(changes);
    }

    public static SyncResponse decode(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        JSONArray array = object.getJSONArray("changes");
        List<SyncChange> changes = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            changes.add(SyncChange.decode(array.getJSONObject(index)));
        }
        return new SyncResponse(object.getLong("accepted_local_seq"),
                object.getLong("next_cursor"), changes);
    }
}
