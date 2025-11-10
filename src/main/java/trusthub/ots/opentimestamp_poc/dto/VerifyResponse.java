package trusthub.ots.opentimestamp_poc.dto;

import java.util.Map;

public class VerifyResponse {
    private String status; // "OK" or "FAIL"
    private String info;
    private String txid;
    private String block_hash;
    private Long block_height;
    private String block_time; // ISO instant string, e.g. 2025-09-21T12:34:56Z
    private Map<?, ?> rawVerifyResults;

    public VerifyResponse() {}

    public VerifyResponse(String status, String info, String txid, String block_hash, Long block_height, String block_time, Map<?, ?> rawVerifyResults) {
        this.status = status;
        this.info = info;
        this.txid = txid;
        this.block_hash = block_hash;
        this.block_height = block_height;
        this.block_time = block_time;
        this.rawVerifyResults = rawVerifyResults;
    }

    // getters / setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInfo() { return info; }
    public void setInfo(String info) { this.info = info; }

    public String getTxid() { return txid; }
    public void setTxid(String txid) { this.txid = txid; }

    public String getBlock_hash() { return block_hash; }
    public void setBlock_hash(String block_hash) { this.block_hash = block_hash; }

    public Long getBlock_height() { return block_height; }
    public void setBlock_height(Long block_height) { this.block_height = block_height; }

    public String getBlock_time() { return block_time; }
    public void setBlock_time(String block_time) { this.block_time = block_time; }

    public Map<?, ?> getRawVerifyResults() { return rawVerifyResults; }
    public void setRawVerifyResults(Map<?, ?> rawVerifyResults) { this.rawVerifyResults = rawVerifyResults; }
}
