package vn.tamtoc.realtime;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class NetworkMetricsTest {
    @Test void percentilesUseRealSamplesAndSinceTick(){var m=new NetworkMetrics();for(int i=1;i<=100;i++)m.tick(i*1000000L,50);assertEquals(95d,m.percentiles(0).get("p95TickMs"));assertEquals(98d,m.percentiles(50).get("p95TickMs"));assertEquals(50,m.percentiles(50).get("tickSamples"));assertEquals(50,m.slowTicks.sum());}
    @Test void sampleStorageIsBoundedAndEmptyWindowsAreExplicit(){var m=new NetworkMetrics();assertEquals(0,m.percentiles(0).get("tickSamples"));for(int i=1;i<=20100;i++)m.tick(i,50);assertEquals(20000,m.percentiles(0).get("tickSamples"));assertEquals(0,m.percentiles(20100).get("tickSamples"));}
}
