package htsjdk.tribble.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static java.lang.Thread.sleep;

/**
 * Created by farjoun on 2/15/17.
 */
public class ConcurrentMappingIteratorTest {

    private static Random rng = new Random();

    private static Integer timesTwoWithRandomWait(Integer x) throws InterruptedException {

        sleep(rng.nextInt()%10+10);

        return x*2;
    }


    @DataProvider(name="sizes")
    public Object[][] testSimpleMapData() {
        return new Object[][]{
                {100, 5, 10},
                {1000, 5, 10},
                {1000, 5, 2},
                {1000, 50, 10},
                {100, 1, 1}
        };
    }

    @Test(dataProvider = "sizes")
    public void testSimpleMap(int listSize, int nThreads, int nQueueSize) {


        Iterator<Integer> i = new ConcurrentMappingIterator<>(IntStream.range(0, listSize).iterator(), x -> {
            try {
                return timesTwoWithRandomWait(x);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return 0;
            }
        }, nThreads, nQueueSize);

        List<Integer> listComp = new ArrayList<>();
        IntStream.range(0, listSize).map( x -> x * 2).forEach(listComp::add);

        List<Integer> list = new ArrayList<>();

        i.forEachRemaining(list::add);

        Assert.assertEquals(list,listComp);
    }
}