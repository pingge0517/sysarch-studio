import com.polarion.platform.ITransactionService;
import java.util.concurrent.Callable;

/** Compile-only signature check. Never loaded by Polarion and never run as a transaction probe. */
public final class SdkCompileProbe {
    private SdkCompileProbe() { }

    public static <T> T inWriteTransaction(ITransactionService transactions, Callable<T> operation) {
        return transactions.execute().write().callable(operation);
    }
}
