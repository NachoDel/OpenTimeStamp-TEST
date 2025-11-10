package trusthub.ots.opentimestamp_poc.models;

import java.time.Instant;
import java.util.UUID;

public class OtsFile {

    private UUID id;
    private String originalFileName;
    private String fileHash;
    private byte[] otsData;
    private String status;
    private String txid;
    private String blockHash;
    private Long blockHeight;
    private Instant blockTime;
    private Instant createdAt;

    public OtsFile() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public byte[] getOtsData() { return otsData; }
    public void setOtsData(byte[] otsData) { this.otsData = otsData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTxid() { return txid; }
    public void setTxid(String txid) { this.txid = txid; }

    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }

    public Long getBlockHeight() { return blockHeight; }
    public void setBlockHeight(Long blockHeight) { this.blockHeight = blockHeight; }

    public Instant getBlockTime() { return blockTime; }
    public void setBlockTime(Instant blockTime) { this.blockTime = blockTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}