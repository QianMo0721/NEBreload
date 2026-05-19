package cn.ussshenzhou.notenoughbandwidth.network.payload;

public class NetworkChannel {
    private final String id;
    private final String version;

    public NetworkChannel(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }
}
