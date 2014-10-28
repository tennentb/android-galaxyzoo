package com.murrayc.galaxyzoo.app.provider.client;

import android.util.JsonReader;

import com.murrayc.galaxyzoo.app.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by murrayc on 10/8/14.
 */
public class MoreItemsJsonParser {
    public static List<ZooniverseClient.Subject> parseMoreItemsResponseContent(final String content) {
        final Reader reader = new StringReader(content);
        final List<ZooniverseClient.Subject> result = parseMoreItemsResponseContent(reader);

        try {
            reader.close();
        } catch (final IOException e) {
            Log.error("MoreItemsJsonParser.parseMoreItemsResponseContent(): StringReader.close() failed", e);
        }

        return result;
    }

    public static List<ZooniverseClient.Subject> parseMoreItemsResponseContent(final InputStream content) {
        final Reader reader = new InputStreamReader(content);
        final List<ZooniverseClient.Subject> result = parseMoreItemsResponseContent(reader);

        try {
            reader.close();
        } catch (final IOException e) {
            Log.error("MoreItemsJsonParser.parseMoreItemsResponseContent(): InputStreamReader.close() failed", e);
        }

        return result;    }

    private static List<ZooniverseClient.Subject> parseMoreItemsResponseContent(final Reader contentReader) {
        final List<ZooniverseClient.Subject> result = new ArrayList<>();

        final JsonReader reader;
        try {
            reader = new JsonReader(contentReader);
            reader.beginArray();
            while (reader.hasNext()) {
                while (reader.hasNext()) {
                    final ZooniverseClient.Subject subject = parseMoreItemsJsonObjectSubject(reader);
                    if (subject != null) {
                        result.add(subject);
                    }
                }
            }
            reader.endArray();
            reader.close();
        } catch (final UnsupportedEncodingException e) {
            Log.info("parseMoreItemsResponseContent: UnsupportedEncodingException parsing JSON", e);
        } catch (final IOException e) {
            Log.info("parseMoreItemsResponseContent: IOException parsing JSON", e);
        } catch (final IllegalStateException e) {
            Log.info("parseMoreItemsResponseContent: IllegalStateException parsing JSON", e);
        }

        if (result.size() == 0) {
            Log.error("Failed. No JSON entities parsed."); //TODO: Use some constant error code?
        }

        //TODO: If this is 0 then something went wrong. Let the user know,
        //maybe via the parseMoreItemsJsonObjectSubject() return string..
        //For instance, the Galaxy-Zoo server could be down for maintenance (this has happened before),
        //or there could be some other network problem.
        return result;
    }

    private static ZooniverseClient.Subject parseMoreItemsJsonObjectSubject(final JsonReader reader) throws IOException {
        reader.beginObject();

        String subjectId = null;
        String zooniverseId = null;
        Locations locations = null;

        while (reader.hasNext()) {
            final String name = reader.nextName();
            switch (name) {
                case "id":
                    subjectId = reader.nextString();
                    break;
                case "zooniverse_id":
                    zooniverseId = reader.nextString();
                    break;
                case "location":
                    locations = parseMoreItemsJsonObjectSubjectLocation(reader);
                    break;
                default:
                    reader.skipValue();
            }
        }

        reader.endObject();

        return new ZooniverseClient.Subject(subjectId, zooniverseId,
                locations.getLocationStandard(), locations.getLocationThumbnail(), locations.getLocationInverted());
    }

    /**
     * This is meant to be immutable.
     */
    final static class Locations {
        private final String mLocationStandard;
        private final String mLocationThumbnail;
        private final String mLocationInverted;

        Locations(final String locationStandard, final String locationThumbnail, final String locationInverted) {
            this.mLocationStandard = locationStandard;
            this.mLocationThumbnail = locationThumbnail;
            this.mLocationInverted = locationInverted;
        }

        public String getLocationStandard() {
            return mLocationStandard;
        }

        public String getLocationThumbnail() {
            return mLocationThumbnail;
        }

        public String getLocationInverted() {
            return mLocationInverted;
        }
    }

    private static Locations parseMoreItemsJsonObjectSubjectLocation(final JsonReader reader) throws IOException {
        reader.beginObject();

        String locationStandard = null;
        String locationThumbnail = null;
        String locationInverted = null;

        while (reader.hasNext()) {
            final String name = reader.nextName();
            switch (name) {
                case "standard":
                    locationStandard = reader.nextString();
                    break;
                case "thumbnail":
                    locationThumbnail = reader.nextString();
                    break;
                case "inverted":
                    locationInverted = reader.nextString();
                    break;
                default:
                    reader.skipValue();
            }
        }

        reader.endObject();

        return new Locations(locationStandard, locationThumbnail, locationInverted);
    }

}
