//
// Triple Play - utilities for use in PlayN-based games
// Copyright (c) 2011-2012, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/tripleplay/blob/master/LICENSE

package tripleplay.syncdb;

import java.util.HashMap;
import java.util.Map;

import react.RMap;
import react.RSet;
import react.Value;

import playn.core.Storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.junit.*;
import static org.junit.Assert.*;

public class SyncDBTest
{
    public static class TestDB extends SyncDB {
        public final Value<Boolean> trueBool = value(
            "trueBool", false, Codec.BOOLEAN, Resolver.TRUE);
        public final Value<Boolean> falseBool = value(
            "falseBool", true, Codec.BOOLEAN, Resolver.FALSE);
        public final Value<Integer> maxInt = value("maxInt", 0, Codec.INT, Resolver.INTMAX);
        public final Value<Long> maxLong = value("maxLong", 0L, Codec.LONG, Resolver.LONGMAX);
        public final Value<String> serverString = value(
            "serverString", null, Codec.STRING, Resolver.SERVER);
        public final RSet<String> unionSet = set("unionSet", Codec.STRING, SetResolver.UNION);
        public final RSet<String> interSet = set(
            "interSet", Codec.STRING, SetResolver.INTERSECTION);
        public final RSet<String> serverSet = set("serverSet", Codec.STRING, SetResolver.SERVER);
        public final RMap<String,Integer> maxMap = map(
            "maxMap", Codec.STRING, Codec.INT, Resolver.INTMAX);

        public TestDB () {
            this(testStorage());
        }

        public TestDB (Storage storage) {
            super(storage);
        }

        public TestDB clone () {
            return new TestDB(_storage);
        }

        public void assertEquals (TestDB other) {
            Assert.assertEquals(trueBool, other.trueBool);
            Assert.assertEquals(falseBool, other.falseBool);
            Assert.assertEquals(maxInt, other.maxInt);
            Assert.assertEquals(maxLong, other.maxLong);
            Assert.assertEquals(serverString, other.serverString);
            Assert.assertEquals(unionSet, other.unionSet);
            Assert.assertEquals(interSet, other.interSet);
            Assert.assertEquals(serverSet, other.serverSet);
            Assert.assertEquals(maxMap, other.maxMap);
        }
    }

    public static class Server {
        public static class Result {
            public final int version;
            public final Map<String,String> delta;
            public final boolean cleanSync;
            public Result (int version) {
                this(version, new HashMap<String,String>(), true);
            }
            public Result (int version, Map<String,String> delta) {
                this(version, delta, false);
            }
            protected Result (int version, Map<String,String> delta, boolean cleanSync) {
                this.version = version;
                this.delta = delta;
                this.cleanSync = cleanSync;
            }
        }

        public Result sync (int clientVers, Map<String,String> delta) {
            if (clientVers > _version) {
                throw new IllegalStateException("So impossible! " + clientVers + " > " + _version);
            } else if (clientVers < _version) {
                return needSync(clientVers);
            } else if (delta.size() == 0) {
                return new Result(_version);
            } else {
                _version += 1;
                for (Map.Entry<String,String> entry : delta.entrySet()) {
                    _data.put(entry.getKey(), new Datum(_version, entry.getValue()));
                }
                return new Result(_version);
            }
        }

        protected Result needSync (int clientVers) {
            Map<String,String> delta = new HashMap<String,String>();
            for (Map.Entry<String,Datum> entry : _data.entrySet()) {
                Datum d = entry.getValue();
                if (d.version > clientVers) delta.put(entry.getKey(), d.value);
            }
            return new Result(_version, delta);
        }

        protected static class Datum {
            public final int version;
            public final String value;
            public Datum (int version, String value) {
                this.version = version;
                this.value = value;
            }
        }

        protected int _version;
        protected Map<String,Datum> _data = new HashMap<String,Datum>();
    }

    @Test public void testSimpleSync () {
        Server server = new Server();
        TestDB one = new TestDB(), two = new TestDB();
        makeTestChanges1(one);
        sync(one, server);
        sync(two, server);
        one.assertEquals(two);
    }

    @Test public void testMaxing () {
        Server server = new Server();
        TestDB one = new TestDB();
        TestDB two = new TestDB();

        // start with some synced changes
        one.maxInt.update(42);
        one.maxLong.update(60L);
        sync(one, server);
        sync(two, server);

        // now make conflicting changes to both client one and two and resync
        one.maxInt.update(40);
        one.maxLong.update(65L);
        two.maxInt.update(45);
        two.maxLong.update(30L);
        sync(one, server); // this will go through no questions asked
        sync(two, server); // this will sync one's changes into two and overwrite some of one's
                           // changes with the merged data from two
        sync(one, server); // this will sync two's merged data back to one

        one.assertEquals(two);
        assertEquals(45, one.maxInt.get().intValue());
        assertEquals(65L, one.maxLong.get().longValue());
    }

    @Test public void testUseServer () {
        Server server = new Server();
        TestDB one = new TestDB();
        TestDB two = new TestDB();

        // start with some synced changes
        one.serverString.update("foo");
        sync(one, server);
        sync(two, server);

        // now make conflicting changes to both client one and two and resync
        one.serverString.update("bar");
        two.serverString.update("baz");
        sync(one, server); // this will go through no questions asked
        sync(two, server); // this will sync one's changes into two

        one.assertEquals(two);
        assertEquals("bar", two.serverString.get());
    }

    @Test public void testSets () {
        Server server = new Server();
        TestDB one = new TestDB();
        TestDB two = new TestDB();

        // start with some synced changes
        one.unionSet.add("one");
        one.unionSet.add("two");
        one.interSet.add("1");
        one.interSet.add("2");
        one.serverSet.add("a");
        one.serverSet.add("b");
        sync(one, server);
        sync(two, server);

        // now make conflicting changes to both client one and two and resync
        one.unionSet.add("three");
        two.unionSet.add("four");
        one.interSet.remove("1");
        two.interSet.add("3");
        one.serverSet.add("c");
        two.serverSet.remove("b");
        sync(one, server); // this will go through no questions asked
        sync(two, server); // this will sync one's changes into two and overwrite some of one's
                           // changes with the merged data from two
        sync(one, server); // this will sync two's merged data back to one

        one.assertEquals(two);
        assertEquals(Sets.newHashSet("one", "two", "three", "four"), two.unionSet);
        assertEquals(Sets.newHashSet("2"), two.interSet);
        assertEquals(Sets.newHashSet("a", "b", "c"), two.serverSet);

        // make sure we reread our data from storage properly
        one.assertEquals(one.clone());
    }

    @Test public void testMap () {
        Server server = new Server();
        TestDB one = new TestDB();
        TestDB two = new TestDB();

        // start with some synced changes
        one.maxMap.put("one", 1);
        one.maxMap.put("two", 2);
        sync(one, server);
        sync(two, server);

        // now make conflicting changes to both client one and two and resync
        one.maxMap.put("three", 3);
        one.maxMap.put("four", 4);
        one.maxMap.remove("one");
        two.maxMap.put("four", 44);
        sync(one, server); // this will go through no questions asked
        sync(two, server); // this will sync one's changes into two and overwrite some of one's
                           // changes with the merged data from two
        sync(one, server); // this will sync two's merged data back to one

        one.assertEquals(two);
        assertEquals(ImmutableMap.of("two", 2, "three", 3, "four", 44), two.maxMap);

        // make sure we reread our data from storage properly
        one.assertEquals(one.clone());
    }

    protected void sync (TestDB db, Server server) {
        Server.Result result;
        do {
            result = server.sync(db.version(), db.getDelta());
            if (result.cleanSync) db.noteSync(result.version);
            else db.applyDelta(result.version, result.delta);
        } while (db.hasUnsyncedChanges());
    }

    protected void makeTestChanges1 (TestDB db) {
        db.trueBool.update(true);
        db.maxInt.update(42);
        db.maxLong.update(400L);
        db.serverString.update("foo");
        db.unionSet.add("one");
        db.unionSet.add("two");
        db.unionSet.add("three");
        db.interSet.add("four");
        db.serverSet.add("five");
    }

    protected void makeTestChanges2 (TestDB db) {
        db.trueBool.update(false);
        db.maxInt.update(60);
        db.maxLong.update(1000L);
        db.serverString.update("bar");
        db.unionSet.remove("one");
        db.unionSet.add("five");
        db.interSet.add("one");
        db.serverSet.add("six");
    }

    protected static Storage testStorage () {
        return new Storage() {
            public void setItem(String key, String data) throws RuntimeException {
                _data.put(key, data);
            }
            public void removeItem(String key) {
                _data.remove(key);
            }
            public String getItem(String key) {
                return _data.get(key);
            }
            public Iterable<String> keys() {
                return _data.keySet();
            }
            public boolean isPersisted() {
                return true;
            }
            protected final Map<String,String> _data = new HashMap<String,String>();
        };
    }
}
