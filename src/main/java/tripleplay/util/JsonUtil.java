//
// Triple Play - utilities for use in PlayN-based games
// Copyright (c) 2011, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/tripleplay/blob/master/LICENSE

package tripleplay.util;

import java.util.Iterator;
import playn.core.Json;

/**
 * Facilities for parsing JSON data
 */
public class JsonUtil
{
    /**
     * @return the Enum whose name corresponds to string for the given key
     */
    public static <T extends Enum<T>> T getEnum (Json.Object json, String key, Class<T> enumType)
    {
        return Enum.valueOf(enumType, json.getString(key));
    }

    /**
     * @return an Iterable<Json.Object> that iterates the Objects in the Array at the given key,
     * or null if there's no Array at that key.
     *
     * The Iterable will throw an error, during iteration, if any of the items in the Array are
     * not Json.Objects.
     */
    public static Iterable<Json.Object> getArrayObjects (Json.Object json, String key)
    {
        final Json.Array array = json.getArray(key);
        if (array == null) {
            return null;

        } else {
            return new Iterable<Json.Object>() {
                @Override public Iterator<Json.Object> iterator () {
                    return new Iterator<Json.Object>() {
                        @Override public boolean hasNext () {
                            return _index < array.length();
                        }
                        @Override public Json.Object next () {
                            Json.Object obj = array.getObject(_index++);
                            if (obj == null) {
                                throw new RuntimeException("There's no Json.Object at the given " +
                                        "index [index=" + _index + "]");
                            }
                            ++_index;
                            return obj;
                        }
                        @Override public void remove () {
                            throw new UnsupportedOperationException();
                        }

                        protected int _index;
                    };
                }
            };
        }
    }
}