package trusthub.ots.opentimestamp_poc.dto;

public class UpgradeResult {
    private final boolean upgraded;
    private final byte[] otsBytes;

    public UpgradeResult(boolean upgraded, byte[] otsBytes) {
        this.upgraded = upgraded;
        this.otsBytes = otsBytes;
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public byte[] getOtsBytes() {
        return otsBytes;
    }
}
