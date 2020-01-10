package mospan.db_log_with_hierarchy;

public enum LogStatus {
    RUNNING("R"),
    FAILED("F"),
    COMPLETED("C");

    private final String status;

    LogStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
