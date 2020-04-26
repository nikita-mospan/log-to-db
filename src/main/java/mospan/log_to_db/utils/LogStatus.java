package mospan.log_to_db.utils;

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
