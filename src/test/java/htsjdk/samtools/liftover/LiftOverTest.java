/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.liftover;

import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.OverlapDetector;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author alecw@broadinstitute.org
 */
public class LiftOverTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/liftover");
    private static final File CHAIN_FILE = new File(TEST_DATA_DIR, "hg18ToHg19.over.chain");

    private LiftOver liftOver;

    @BeforeClass
    public void initLiftOver() {
        liftOver = new LiftOver(CHAIN_FILE);
    }

    @Test(dataProvider = "testIntervals")
    public void testBasic(final Interval in, final Interval expected) {
        final Interval out = liftOver.liftOver(in);
        Assert.assertEquals(out, expected);

    }

    @DataProvider(name = "testIntervals")
    public Object[][] makeTestIntervals() {
        return new Object[][] {
                {new Interval("chr3", 50911035, 50911051), null},
                {new Interval("chr1", 16776377, 16776452),    new Interval("chr1", 16903790, 16903865)},
                {new Interval("chr2", 30575990, 30576065),    new Interval("chr2", 30722486, 30722561)},
                {new Interval("chr3", 12157217, 12157292),    new Interval("chr3", 12182217, 12182292)},
                {new Interval("chr4", 12503121, 12503196),    new Interval("chr4", 12894023, 12894098)},
                {new Interval("chr5", 13970930, 13971005),    new Interval("chr5", 13917930, 13918005)},
                {new Interval("chr6", 13838774, 13838849),    new Interval("chr6", 13730795, 13730870)},
                {new Interval("chr7", 23978336, 23978411),    new Interval("chr7", 24011811, 24011886)},
                {new Interval("chr8", 13337368, 13337443),    new Interval("chr8", 13292997, 13293072)},
                {new Interval("chr9", 35059282, 35059357),    new Interval("chr9", 35069282, 35069357)},
                {new Interval("chr10", 7893794, 7893869),     new Interval("chr10", 7853788, 7853863)},
                {new Interval("chr11", 17365784, 17365859),   new Interval("chr11", 17409208, 17409283)},
                {new Interval("chr12", 4530193, 4530268),     new Interval("chr12", 4659932, 4660007)},
                {new Interval("chr13", 29398707, 29398782),   new Interval("chr13", 30500707, 30500782)},
                {new Interval("chr14", 22955252, 22955327),   new Interval("chr14", 23885412, 23885487)},
                {new Interval("chr15", 27477379, 27477454),   new Interval("chr15", 29690087, 29690162)},
                {new Interval("chr16", 13016380, 13016455),   new Interval("chr16", 13108879, 13108954)},
                {new Interval("chr17", 28318218, 28318293),   new Interval("chr17", 31294105, 31294180)},
                {new Interval("chr18", 42778225, 42778300),   new Interval("chr18", 44524227, 44524302)},
                {new Interval("chr19", 8340119, 8340194),     new Interval("chr19", 8434119, 8434194)},
                {new Interval("chr20", 39749226, 39749301),   new Interval("chr20", 40315812, 40315887)},
                {new Interval("chr21", 20945136, 20945211),   new Interval("chr21", 22023265, 22023340)},
                {new Interval("chr22", 32307031, 32307106),   new Interval("chr22", 33977031, 33977106)},
                {new Interval("chrX", 34252958, 34253033) ,   new Interval("chrX", 34343037, 34343112)},
                // Sampling from /seq/references/HybSelOligos/whole_exome_refseq_coding/whole_exome_refseq_coding.targets.interval_list
                {new Interval("chr1", 58952, 59873),	new Interval("chr1", 69089, 70010)},
                {new Interval("chr1", 7733844, 7734041),	new Interval("chr1", 7811257, 7811454)},
                {new Interval("chr1", 16261179, 16261276),	new Interval("chr1", 16388592, 16388689)},
                {new Interval("chr1", 23634929, 23635110),	new Interval("chr1", 23762342, 23762523)},
                {new Interval("chr1", 31910910, 31911030),	new Interval("chr1", 32138323, 32138443)},
                {new Interval("chr1", 39686851, 39687024),	new Interval("chr1", 39914264, 39914437)},
                {new Interval("chr1", 46434068, 46434185),	new Interval("chr1", 46661481, 46661598)},
                {new Interval("chr1", 60102890, 60102928),	new Interval("chr1", 60330302, 60330340)},
                {new Interval("chr1", 84734151, 84734336),	new Interval("chr1", 84961563, 84961748)},
                {new Interval("chr1", 100529545, 100529650),	new Interval("chr1", 100756957, 100757062)},
                {new Interval("chr1", 114771320, 114771441),	new Interval("chr1", 114969797, 114969918)},
                {new Interval("chr1", 148564831, 148564965),	new Interval("chr1", 150298207, 150298341)},
                {new Interval("chr1", 153293008, 153293090),	new Interval("chr1", 155026384, 155026466)},
                {new Interval("chr1", 158167550, 158167677),	new Interval("chr1", 159900926, 159901053)},
                {new Interval("chr1", 169444555, 169444718),	new Interval("chr1", 171177931, 171178094)},
                {new Interval("chr1", 183535970, 183536100),	new Interval("chr1", 185269347, 185269477)},
                {new Interval("chr1", 201411300, 201411508),	new Interval("chr1", 203144677, 203144885)},
                {new Interval("chr1", 212862043, 212862249),	new Interval("chr1", 214795420, 214795626)},
                {new Interval("chr1", 228992499, 228992560),	new Interval("chr1", 230925876, 230925937)},
                {new Interval("chr1", 246268191, 246269133),	new Interval("chr1", 248201568, 248202510)},
                {new Interval("chr2", 25027765, 25027929),	new Interval("chr2", 25174261, 25174425)},
                {new Interval("chr2", 32572109, 32572240),	new Interval("chr2", 32718605, 32718736)},
                {new Interval("chr2", 53988959, 53989061),	new Interval("chr2", 54135455, 54135557)},
                {new Interval("chr2", 71749748, 71749847),	new Interval("chr2", 71896240, 71896339)},
                {new Interval("chr2", 96059879, 96060011),	new Interval("chr2", 96696152, 96696284)},
                {new Interval("chr2", 109923614, 109923763),	new Interval("chr2", 110566325, 110566474)},
                {new Interval("chr2", 130655571, 130655646),	new Interval("chr2", 130939101, 130939176)},
                {new Interval("chr2", 159228028, 159228205),	new Interval("chr2", 159519782, 159519959)},
                {new Interval("chr2", 172639236, 172639282),	new Interval("chr2", 172930990, 172931036)},
                {new Interval("chr2", 189558634, 189558751),	new Interval("chr2", 189850389, 189850506)},
                {new Interval("chr2", 203547300, 203547466),	new Interval("chr2", 203839055, 203839221)},
                {new Interval("chr2", 219578985, 219579191),	new Interval("chr2", 219870741, 219870947)},
                {new Interval("chr2", 232982284, 232982404),	new Interval("chr2", 233274040, 233274160)},
                {new Interval("chr3", 3114819, 3114976),	new Interval("chr3", 3139819, 3139976)},
                {new Interval("chr3", 16333337, 16333745),	new Interval("chr3", 16358333, 16358741)},
                {new Interval("chr3", 40183652, 40183736),	new Interval("chr3", 40208648, 40208732)},
                {new Interval("chr3", 48601077, 48601227),	new Interval("chr3", 48626073, 48626223)},
                {new Interval("chr3", 52287255, 52287419),	new Interval("chr3", 52312215, 52312379)},
                {new Interval("chr3", 63979313, 63979425),	new Interval("chr3", 64004273, 64004385)},
                {new Interval("chr3", 110234255, 110234364),	new Interval("chr3", 108751565, 108751674)},
                {new Interval("chr3", 126088466, 126088539),	new Interval("chr3", 124605776, 124605849)},
                {new Interval("chr3", 137600279, 137600363),	new Interval("chr3", 136117589, 136117673)},
                {new Interval("chr3", 159845116, 159845200),	new Interval("chr3", 158362422, 158362506)},
                {new Interval("chr3", 185387877, 185387927),	new Interval("chr3", 183905183, 183905233)},
                {new Interval("chr3", 199065658, 199065715),	new Interval("chr3", 197581261, 197581318)},
                {new Interval("chr4", 10152742, 10152765),	new Interval("chr4", 10543644, 10543667)},
                {new Interval("chr4", 47243396, 47243638),	new Interval("chr4", 47548639, 47548881)},
                {new Interval("chr4", 72632227, 72632303),	new Interval("chr4", 72413363, 72413439)},
                {new Interval("chr4", 88942682, 88942736),	new Interval("chr4", 88723658, 88723712)},
                {new Interval("chr4", 114381088, 114381190),	new Interval("chr4", 114161639, 114161741)},
                {new Interval("chr4", 151338602, 151338707),	new Interval("chr4", 151119152, 151119257)},
                {new Interval("chr4", 184429225, 184429390),	new Interval("chr4", 184192231, 184192396)},
                {new Interval("chr5", 14804176, 14804350),	new Interval("chr5", 14751176, 14751350)},
                {new Interval("chr5", 43687596, 43687745),	new Interval("chr5", 43651839, 43651988)},
                {new Interval("chr5", 71651730, 71651806),	new Interval("chr5", 71615974, 71616050)},
                {new Interval("chr5", 95017504, 95017771),	new Interval("chr5", 94991748, 94992015)},
                {new Interval("chr5", 128984208, 128984352),	new Interval("chr5", 128956309, 128956453)},
                {new Interval("chr5", 140033038, 140033159),	new Interval("chr5", 140052854, 140052975)},
                {new Interval("chr5", 153045976, 153046084),	new Interval("chr5", 153065783, 153065891)},
                {new Interval("chr5", 176255669, 176255768),	new Interval("chr5", 176323063, 176323162)},
                {new Interval("chr6", 10810586, 10810710),	new Interval("chr6", 10702600, 10702724)},
                {new Interval("chr6", 30666289, 30666459),	new Interval("chr6", 30558310, 30558480)},
                {new Interval("chr6", 33082591, 33082598),	new Interval("chr6", 32974613, 32974620)},
                {new Interval("chr6", 39940185, 39940263),	new Interval("chr6", 39832207, 39832285)},
                {new Interval("chr6", 50789726, 50789768),	new Interval("chr6", 50681767, 50681809)},
                {new Interval("chr6", 79721666, 79721720),	new Interval("chr6", 79664947, 79665001)},
                {new Interval("chr6", 108336822, 108336934),	new Interval("chr6", 108230129, 108230241)},
                {new Interval("chr6", 131240935, 131241085),	new Interval("chr6", 131199242, 131199392)},
                {new Interval("chr6", 151799272, 151799384),	new Interval("chr6", 151757579, 151757691)},
                {new Interval("chr6", 169897302, 169897445),	new Interval("chr6", 170155377, 170155520)},
                {new Interval("chr7", 17341792, 17341937),	new Interval("chr7", 17375267, 17375412)},
                {new Interval("chr7", 38875269, 38875380),	new Interval("chr7", 38908744, 38908855)},
                {new Interval("chr7", 72563000, 72563120),	new Interval("chr7", 72925064, 72925184)},
                {new Interval("chr7", 89839403, 89839480),	new Interval("chr7", 90001467, 90001544)},
                {new Interval("chr7", 100063781, 100063867),	new Interval("chr7", 100225845, 100225931)},
                {new Interval("chr7", 111889559, 111889671),	new Interval("chr7", 112102323, 112102435)},
                {new Interval("chr7", 133900771, 133900840),	new Interval("chr7", 134250231, 134250300)},
                {new Interval("chr7", 149124615, 149124769),	new Interval("chr7", 149493682, 149493836)},
                {new Interval("chr8", 9647462, 9647548),	new Interval("chr8", 9610052, 9610138)},
                {new Interval("chr8", 27203588, 27203614),	new Interval("chr8", 27147671, 27147697)},
                {new Interval("chr8", 43171970, 43172044),	new Interval("chr8", 43052813, 43052887)},
                {new Interval("chr8", 76088775, 76088894),	new Interval("chr8", 75926220, 75926339)},
                {new Interval("chr8", 103641854, 103642290),	new Interval("chr8", 103572678, 103573114)},
                {new Interval("chr8", 133913660, 133913828),	new Interval("chr8", 133844478, 133844646)},
                {new Interval("chr8", 145697031, 145697164),	new Interval("chr8", 145726223, 145726356)},
                {new Interval("chr9", 26985517, 26985849),	new Interval("chr9", 26995517, 26995849)},
                {new Interval("chr9", 68496721, 68496793),	new Interval("chr9", 69206901, 69206973)},
                {new Interval("chr9", 94051959, 94052046),	new Interval("chr9", 95012138, 95012225)},
                {new Interval("chr9", 110750285, 110750337),	new Interval("chr9", 111710464, 111710516)},
                {new Interval("chr9", 124416836, 124417782),	new Interval("chr9", 125377015, 125377961)},
                {new Interval("chr9", 130939690, 130939794),	new Interval("chr9", 131899869, 131899973)},
                {new Interval("chr9", 138395593, 138395667),	new Interval("chr9", 139275772, 139275846)},
                {new Interval("chr10", 6048112, 6048310),	new Interval("chr10", 6008106, 6008304)},
                {new Interval("chr10", 26599573, 26599693),	new Interval("chr10", 26559567, 26559687)},
                {new Interval("chr10", 51507890, 51507920),	new Interval("chr10", 51837884, 51837914)},
                {new Interval("chr10", 74343070, 74343234),	new Interval("chr10", 74673064, 74673228)},
                {new Interval("chr10", 93604764, 93604865),	new Interval("chr10", 93614784, 93614885)},
                {new Interval("chr10", 101985412, 101985513),	new Interval("chr10", 101995422, 101995523)},
                {new Interval("chr10", 115325644, 115325755),	new Interval("chr10", 115335654, 115335765)},
                {new Interval("chr10", 129062310, 129062470),	new Interval("chr10", 129172320, 129172480)},
                {new Interval("chr11", 1904274, 1904289),	new Interval("chr11", 1947698, 1947713)},
                {new Interval("chr11", 11928485, 11928607),	new Interval("chr11", 11971909, 11972031)},
                {new Interval("chr11", 33326642, 33326942),	new Interval("chr11", 33370066, 33370366)},
                {new Interval("chr11", 55554469, 55555445),	new Interval("chr11", 55797893, 55798869)},
                {new Interval("chr11", 62505888, 62506060),	new Interval("chr11", 62749312, 62749484)},
                {new Interval("chr11", 65488560, 65488619),	new Interval("chr11", 65731984, 65732043)},
                {new Interval("chr11", 71618353, 71618446),	new Interval("chr11", 71940705, 71940798)},
                {new Interval("chr11", 89174516, 89174750),	new Interval("chr11", 89534868, 89535102)},
                {new Interval("chr11", 111349955, 111350190),	new Interval("chr11", 111844745, 111844980)},
                {new Interval("chr11", 120195672, 120195841),	new Interval("chr11", 120690462, 120690631)},
                {new Interval("chr12", 1089617, 1089776),	new Interval("chr12", 1219356, 1219515)},
                {new Interval("chr12", 8894021, 8894139),	new Interval("chr12", 9002754, 9002872)},
                {new Interval("chr12", 26455518, 26455614),	new Interval("chr12", 26564251, 26564347)},
                {new Interval("chr12", 46663731, 46663788),	new Interval("chr12", 48377464, 48377521)},
                {new Interval("chr12", 51502394, 51502432),	new Interval("chr12", 53216127, 53216165)},
                {new Interval("chr12", 55603883, 55604103),	new Interval("chr12", 57317616, 57317836)},
                {new Interval("chr12", 69218200, 69218280),	new Interval("chr12", 70931933, 70932013)},
                {new Interval("chr12", 97543837, 97544677),	new Interval("chr12", 99019706, 99020546)},
                {new Interval("chr12", 108438951, 108439074),	new Interval("chr12", 109954568, 109954691)},
                {new Interval("chr12", 119021215, 119021343),	new Interval("chr12", 120536832, 120536960)},
                {new Interval("chr12", 127849755, 127849917),	new Interval("chr12", 129283802, 129283964)},
                {new Interval("chr13", 28900978, 28901035),	new Interval("chr13", 30002978, 30003035)},
                {new Interval("chr13", 48646570, 48646698),	new Interval("chr13", 49748569, 49748697)},
                {new Interval("chr13", 98989699, 98989814),	new Interval("chr13", 100191698, 100191813)},
                {new Interval("chr14", 20929460, 20929643),	new Interval("chr14", 21859620, 21859803)},
                {new Interval("chr14", 33338689, 33340068),	new Interval("chr14", 34268938, 34270317)},
                {new Interval("chr14", 55217155, 55217163),	new Interval("chr14", 56147402, 56147410)},
                {new Interval("chr14", 71260115, 71260358),	new Interval("chr14", 72190362, 72190605)},
                {new Interval("chr14", 89806293, 89806451),	new Interval("chr14", 90736540, 90736698)},
                {new Interval("chr14", 102548185, 102548280),	new Interval("chr14", 103478432, 103478527)},
                {new Interval("chr15", 31917122, 31918453),	new Interval("chr15", 34129830, 34131161)},
                {new Interval("chr15", 40481129, 40481302),	new Interval("chr15", 42693837, 42694010)},
                {new Interval("chr15", 48649374, 48649484),	new Interval("chr15", 50862082, 50862192)},
                {new Interval("chr15", 61768839, 61768953),	new Interval("chr15", 63981786, 63981900)},
                {new Interval("chr15", 72115399, 72115456),	new Interval("chr15", 74328346, 74328403)},
                {new Interval("chr15", 83031858, 83032011),	new Interval("chr15", 85230854, 85231007)},
                {new Interval("chr16", 79709, 79902),	new Interval("chr16", 139709, 139902)},
                {new Interval("chr16", 2285590, 2285744),	new Interval("chr16", 2345589, 2345743)},
                {new Interval("chr16", 14872977, 14873044),	new Interval("chr16", 14965476, 14965543)},
                {new Interval("chr16", 23611004, 23611155),	new Interval("chr16", 23703503, 23703654)},
                {new Interval("chr16", 31004784, 31005007),	new Interval("chr16", 31097283, 31097506)},
                {new Interval("chr16", 55745701, 55745922),	new Interval("chr16", 57188200, 57188421)},
                {new Interval("chr16", 66647766, 66647830),	new Interval("chr16", 68090265, 68090329)},
                {new Interval("chr16", 79224415, 79224636),	new Interval("chr16", 80666914, 80667135)},
                {new Interval("chr17", 1320663, 1320735),	new Interval("chr17", 1373913, 1373985)},
                {new Interval("chr17", 5304981, 5305155),	new Interval("chr17", 5364257, 5364431)},
                {new Interval("chr17", 8588568, 8588654),	new Interval("chr17", 8647843, 8647929)},
                {new Interval("chr17", 18192362, 18192481),	new Interval("chr17", 18251637, 18251756)},
                {new Interval("chr17", 26514328, 26514522),	new Interval("chr17", 29490202, 29490396)},
                {new Interval("chr17", 35069238, 35069334),	new Interval("chr17", 37815712, 37815808)},
                {new Interval("chr17", 38377148, 38377241),	new Interval("chr17", 41123622, 41123715)},
                {new Interval("chr17", 44472316, 44472454),	new Interval("chr17", 47117317, 47117455)},
                {new Interval("chr17", 55482984, 55483122),	new Interval("chr17", 58128202, 58128340)},
                {new Interval("chr17", 64595087, 64595211),	new Interval("chr17", 67083492, 67083616)},
                {new Interval("chr17", 72814816, 72814876),	new Interval("chr17", 75303221, 75303281)},
                {new Interval("chr17", 78167687, 78167812),	new Interval("chr17", 80574398, 80574523)},
                {new Interval("chr18", 19653801, 19653961),	new Interval("chr18", 21399803, 21399963)},
                {new Interval("chr18", 46766985, 46767455),	new Interval("chr18", 48512987, 48513457)},
                {new Interval("chr19", 822924, 823120),	new Interval("chr19", 871924, 872120)},
                {new Interval("chr19", 4200223, 4200327),	new Interval("chr19", 4249223, 4249327)},
                {new Interval("chr19", 8094666, 8094894),	new Interval("chr19", 8188666, 8188894)},
                {new Interval("chr19", 11657040, 11657607),	new Interval("chr19", 11796040, 11796607)},
                {new Interval("chr19", 16298665, 16298844),	new Interval("chr19", 16437665, 16437844)},
                {new Interval("chr19", 19650533, 19650597),	new Interval("chr19", 19789533, 19789597)},
                {new Interval("chr19", 42008351, 42008363),	new Interval("chr19", 37316511, 37316523)},
                {new Interval("chr19", 46446486, 46446567),	new Interval("chr19", 41754646, 41754727)},
                {new Interval("chr19", 51212087, 51212169),	new Interval("chr19", 46520247, 46520329)},
                {new Interval("chr19", 55052042, 55052201),	new Interval("chr19", 50360230, 50360389)},
                {new Interval("chr19", 60200495, 60200669),	new Interval("chr19", 55508683, 55508857)},
                {new Interval("chr20", 3244380, 3244434),	new Interval("chr20", 3296380, 3296434)},
                {new Interval("chr20", 25145282, 25145374),	new Interval("chr20", 25197282, 25197374)},
                {new Interval("chr20", 35182714, 35182855),	new Interval("chr20", 35749300, 35749441)},
                {new Interval("chr20", 46797751, 46797826),	new Interval("chr20", 47364344, 47364419)},
                {new Interval("chr20", 61546454, 61546633),	new Interval("chr20", 62076010, 62076189)},
                {new Interval("chr21", 36666540, 36666701),	new Interval("chr21", 37744670, 37744831)},
                {new Interval("chr21", 46450176, 46450285),	new Interval("chr21", 47625748, 47625857)},
                {new Interval("chr22", 22890366, 22890533),	new Interval("chr22", 24560366, 24560533)},
                {new Interval("chr22", 32487356, 32487465),	new Interval("chr22", 34157356, 34157465)},
                {new Interval("chr22", 40469028, 40469146),	new Interval("chr22", 42139082, 42139200)},
                {new Interval("chr22", 49365651, 49365713),	new Interval("chr22", 51018785, 51018847)},
                {new Interval("chrX", 24135748, 24135895),	new Interval("chrX", 24225827, 24225974)},
                {new Interval("chrX", 48708293, 48708459),	new Interval("chrX", 48823349, 48823515)},
                {new Interval("chrX", 69406673, 69406721),	new Interval("chrX", 69489948, 69489996)},
                {new Interval("chrX", 101459444, 101459531),	new Interval("chrX", 101572788, 101572875)},
                {new Interval("chrX", 128442357, 128442474),	new Interval("chrX", 128614676, 128614793)},
                {new Interval("chrX", 152701873, 152701902),	new Interval("chrX", 153048679, 153048708)},
                {new Interval("chrY", 2715028, 2715646),	new Interval("chrY", 2655028, 2655646)},
                {new Interval("chrY", 26179988, 26180064),	new Interval("chrY", 27770600, 27770676)},
                // Some intervals that are flipped in the new genome
                {new Interval("chr1", 2479704, 2479833, false, "target_549"),        new Interval("chr1", 2494585, 2494714, true, "target_549")},
                {new Interval("chr1", 2480081, 2480116, false, "target_550"),        new Interval("chr1", 2494302, 2494337, true, "target_550")},
                {new Interval("chr1", 2481162, 2481308, false, "target_551"),        new Interval("chr1", 2493110, 2493256, true, "target_551")},
                {new Interval("chr1", 2482263, 2482357, false, "target_552"),        new Interval("chr1", 2492061, 2492155, true, "target_552")},
                {new Interval("chr1", 2482999, 2483158, false, "target_553"),        new Interval("chr1", 2491260, 2491419, true, "target_553")},
                {new Interval("chr1", 2484509, 2484638, false, "target_554"),        new Interval("chr1", 2489780, 2489909, true, "target_554")},
                {new Interval("chr1", 2485143, 2485255, false, "target_555"),        new Interval("chr1", 2489163, 2489275, true, "target_555")},
                {new Interval("chr1", 2486244, 2486316, false, "target_556"),        new Interval("chr1", 2488102, 2488174, true, "target_556")},
                {new Interval("chr2", 110735471, 110735558, false, "target_101982"), new Interval("chr2", 110585640, 110585727, true, "target_101982")},
                {new Interval("chr2", 110735648, 110735831, false, "target_101983"), new Interval("chr2", 110585367, 110585550, true, "target_101983")},
                {new Interval("chr2", 110736772, 110736922, false, "target_101984"), new Interval("chr2", 110584276, 110584426, true, "target_101984")},
                {new Interval("chr2", 110737181, 110737322, false, "target_101985"), new Interval("chr2", 110583876, 110584017, true, "target_101985")},
                {new Interval("chr2", 110737585, 110737747, false, "target_101986"), new Interval("chr2", 110583451, 110583613, true, "target_101986")},
                {new Interval("chr2", 110738666, 110738793, false, "target_101987"), new Interval("chr2", 110582405, 110582532, true, "target_101987")},
                {new Interval("chr2", 110738957, 110739136, false, "target_101988"), new Interval("chr2", 110582062, 110582241, true, "target_101988")},
                {new Interval("chr2", 110739216, 110739401, false, "target_101989"), new Interval("chr2", 110581797, 110581982, true, "target_101989")},
                {new Interval("chr2", 110741555, 110741768, false, "target_101990"), new Interval("chr2", 110579480, 110579693, true, "target_101990")},
                {new Interval("chr2", 110743887, 110743978, false, "target_101991"), new Interval("chr2", 110577271, 110577362, true, "target_101991")},
                {new Interval("chr2", 110750021, 110750220, false, "target_101992"), new Interval("chr2", 110571035, 110571234, true, "target_101992")},
                {new Interval("chr2", 110754786, 110754935, false, "target_101993"), new Interval("chr2", 110566325, 110566474, true, "target_101993")},
                {new Interval("chr2", 110755277, 110755511, false, "target_101994"), new Interval("chr2", 110565749, 110565983, true, "target_101994")},
                {new Interval("chr2", 110759547, 110759703, false, "target_101995"), new Interval("chr2", 110561554, 110561710, true, "target_101995")},
                {new Interval("chr2", 110760135, 110760250, false, "target_101996"), new Interval("chr2", 110561007, 110561122, true, "target_101996")},
                {new Interval("chr2", 110761828, 110761899, false, "target_101997"), new Interval("chr2", 110559358, 110559429, true, "target_101997")},
                {new Interval("chr2", 110769521, 110769596, false, "target_101998"), new Interval("chr2", 110552041, 110552116, true, "target_101998")},
                {new Interval("chr2", 111012182, 111012298, false, "target_101999"), new Interval("chr2", 108484181, 108484297, true, "target_101999")},
                {new Interval("chr13", 113547048, 113547139, false, "target_51005"), new Interval("chr13", 114566804, 114566895, true, "target_51005")},
                {new Interval("chr13", 113547227, 113547397, false, "target_51006"), new Interval("chr13", 114566546, 114566716, true, "target_51006")},
                {new Interval("chr13", 113562918, 113562946, false, "target_51007"), new Interval("chr13", 114550997, 114551025, true, "target_51007")},
                {new Interval("chr13", 113564379, 113564445, false, "target_51008"), new Interval("chr13", 114549498, 114549564, true, "target_51008")},
                {new Interval("chr13", 113571118, 113571244, false, "target_51009"), new Interval("chr13", 114542699, 114542825, true, "target_51009")},
                {new Interval("chr13", 113572777, 113572903, false, "target_51010"), new Interval("chr13", 114541040, 114541166, true, "target_51010")},
                {new Interval("chr13", 113575333, 113575459, false, "target_51011"), new Interval("chr13", 114538484, 114538610, true, "target_51011")},
                {new Interval("chr13", 113576296, 113576421, false, "target_51012"), new Interval("chr13", 114537522, 114537647, true, "target_51012")},
                {new Interval("chr13", 113578216, 113578338, false, "target_51013"), new Interval("chr13", 114535605, 114535727, true, "target_51013")},
                {new Interval("chr13", 113578480, 113578673, false, "target_51014"), new Interval("chr13", 114535270, 114535463, true, "target_51014")},
                {new Interval("chr13", 113582257, 113582425, false, "target_51015"), new Interval("chr13", 114531518, 114531686, true, "target_51015")},
                {new Interval("chr13", 113583804, 113583976, false, "target_51016"), new Interval("chr13", 114529967, 114530139, true, "target_51016")},
                {new Interval("chr13", 113587418, 113587597, false, "target_51017"), new Interval("chr13", 114526346, 114526525, true, "target_51017")},
                {new Interval("chr13", 113588782, 113589014, false, "target_51018"), new Interval("chr13", 114524929, 114525161, true, "target_51018")},
                {new Interval("chr13", 113589950, 113590108, false, "target_51019"), new Interval("chr13", 114523835, 114523993, true, "target_51019")},
                {new Interval("chr13", 113599065, 113599236, false, "target_51020"), new Interval("chr13", 114514707, 114514878, true, "target_51020")},
                {new Interval("chr13", 113605940, 113606087, false, "target_51021"), new Interval("chr13", 114507856, 114508003, true, "target_51021")},
                {new Interval("chr13", 113609156, 113609319, false, "target_51022"), new Interval("chr13", 114504624, 114504787, true, "target_51022")},
                {new Interval("chr13", 113610056, 113610145, false, "target_51023"), new Interval("chr13", 114503798, 114503887, true, "target_51023")},
                {new Interval("chr13", 113611549, 113611633, false, "target_51024"), new Interval("chr13", 114502310, 114502394, true, "target_51024")},
                {new Interval("chr13", 113615731, 113615824, false, "target_51025"), new Interval("chr13", 114498119, 114498212, true, "target_51025")},
                {new Interval("chr13", 113641808, 113641874, false, "target_51026"), new Interval("chr13", 114472069, 114472135, true, "target_51026")},
                {new Interval("chr13", 113644711, 113644857, false, "target_51027"), new Interval("chr13", 114469086, 114469232, true, "target_51027")},
                {new Interval("chr13", 113651799, 113651848, false, "target_51028"), new Interval("chr13", 114462241, 114462290, true, "target_51028")},
                {new Interval("chr17", 33541604, 33542176, false, "target_76102"),   new Interval("chr17", 36294030, 36294602, true, "target_76102")},
                {new Interval("chr17", 33543154, 33543310, false, "target_76103"),   new Interval("chr17", 36292896, 36293052, true, "target_76103")},
                {new Interval("chr17", 33543677, 33543780, false, "target_76104"),   new Interval("chr17", 36292426, 36292529, true, "target_76104")},
                {new Interval("chr17", 33544240, 33544309, false, "target_76105"),   new Interval("chr17", 36291897, 36291966, true, "target_76105")},
                {new Interval("chr17", 33544690, 33544788, false, "target_76106"),   new Interval("chr17", 36291418, 36291516, true, "target_76106")},
                {new Interval("chr17", 33545498, 33545622, false, "target_76107"),   new Interval("chr17", 36290584, 36290708, true, "target_76107")},
                {new Interval("chr17", 33547465, 33547578, false, "target_76109"),   new Interval("chr17", 36288629, 36288742, true, "target_76109")},
                {new Interval("chr17", 33547904, 33548015, false, "target_76110"),   new Interval("chr17", 36288192, 36288303, true, "target_76110")},
                {new Interval("chr17", 33548455, 33548539, false, "target_76111"),   new Interval("chr17", 36287668, 36287752, true, "target_76111")},
                {new Interval("chr17", 33549018, 33549061, false, "target_76112"),   new Interval("chr17", 36287146, 36287189, true, "target_76112")},
                {new Interval("chr17", 33550341, 33550430, false, "target_76113"),   new Interval("chr17", 36285777, 36285866, true, "target_76113")},
                {new Interval("chr17", 33550589, 33550664, false, "target_76114"),   new Interval("chr17", 36285543, 36285618, true, "target_76114")},
                {new Interval("chrX", 148575967, 148576994, false, "target_184692"), new Interval("chrX", 148797411, 148798438, true, "target_184692")},
                {new Interval("chrX", 148577066, 148577143, false, "target_184693"), new Interval("chrX", 148797262, 148797339, true, "target_184693")},
                {new Interval("chrX", 148578167, 148578266, false, "target_184694"), new Interval("chrX", 148796139, 148796238, true, "target_184694")},
                {new Interval("chrX", 148579488, 148579587, false, "target_184695"), new Interval("chrX", 148794818, 148794917, true, "target_184695")},
                {new Interval("chrX", 148603758, 148603770, false, "target_184696"), new Interval("chrX", 148770634, 148770646, true, "target_184696")},
                // Some that don't map in hg19
                {new Interval("chr2", 111013693, 111013832), null},
                {new Interval("chr3", 14174511, 14175398), null},
                {new Interval("chr3", 50911035, 50911051), null},
                {new Interval("chr6", 32071709, 32071869), null},
                {new Interval("chr6", 32072183, 32072358), null},
                {new Interval("chr6", 32104446, 32104606), null},
                {new Interval("chr6", 32104920, 32105095), null},
                {new Interval("chr7", 101995561, 101995739), null},
                {new Interval("chr7", 142178782, 142178825), null},
                {new Interval("chr7", 142179850, 142180013), null},
                {new Interval("chr7", 142181067, 142181324), null},
                {new Interval("chr7", 142181720, 142181860), null},
                {new Interval("chr7", 142182157, 142182313), null},
                {new Interval("chr15", 19335778, 19336302), null},
                {new Interval("chr17", 33364376, 33364428), null},
                {new Interval("chr17", 33546162, 33546214), null},
                {new Interval("chr17", 33706667, 33706736), null},
                {new Interval("chr17", 59772721, 59772781), null},
                {new Interval("chr17", 59779355, 59779421), null},
                {new Interval("chr17", 59781483, 59781540), null},
                {new Interval("chr17", 59783488, 59783565), null},
                {new Interval("chr17", 59784584, 59784615), null},
                {new Interval("chr17", 59786025, 59786136), null},
                {new Interval("chr17", 59787203, 59787494), null},
                {new Interval("chr17", 59791235, 59791514), null},
                {new Interval("chr17", 59794247, 59794502), null},
                {new Interval("chr17", 59801884, 59802193), null},
                {new Interval("chr17", 59804685, 59804982), null},
                {new Interval("chr17", 59817352, 59817382), null},
                {new Interval("chr17", 59817465, 59817532), null},
                {new Interval("chr17", 59875754, 59875812), null},
                {new Interval("chr17", 59875899, 59875944), null},
                {new Interval("chr17", 59879183, 59879456), null},
                {new Interval("chr17", 59883988, 59884276), null},
                {new Interval("chr17", 59887398, 59887512), null},
                {new Interval("chrX", 48774611, 48775058), null},

        };
    }

    @Test(dataProvider = "failingIntervals")
    public void testDiagnosticLiftover(final Interval fromInterval) {
        final List<LiftOver.PartialLiftover> partials = liftOver.diagnosticLiftover(fromInterval);
        System.out.println("Diagnosing " + fromInterval + " (len " + fromInterval.length() + ")");
        for (final LiftOver.PartialLiftover partial : partials) {
            System.out.println(partial);
        }
    }

    @DataProvider(name = "failingIntervals")
    public Object[][] makeFailingIntervals() {
        return new Object[][] {
                {new Interval("chr3", 50911035, 50911051)},
                {new Interval("chr2", 111013693, 111013832)},
                {new Interval("chr3", 14174511, 14175398)},
                {new Interval("chr3", 50911035, 50911051)},
                {new Interval("chr6", 32071709, 32071869)},
                {new Interval("chr6", 32072183, 32072358)},
                {new Interval("chr6", 32104446, 32104606)},
                {new Interval("chr6", 32104920, 32105095)},
                {new Interval("chr7", 101995561, 101995739)},
                {new Interval("chr7", 142178782, 142178825)},
                {new Interval("chr7", 142179850, 142180013)},
                {new Interval("chr7", 142181067, 142181324)},
                {new Interval("chr7", 142181720, 142181860)},
                {new Interval("chr7", 142182157, 142182313)},
                {new Interval("chr15", 19335778, 19336302)},
                {new Interval("chr17", 33364376, 33364428)},
                {new Interval("chr17", 33546162, 33546214)},
                {new Interval("chr17", 33706667, 33706736)},
                {new Interval("chr17", 59772721, 59772781)},
                {new Interval("chr17", 59779355, 59779421)},
                {new Interval("chr17", 59781483, 59781540)},
                {new Interval("chr17", 59783488, 59783565)},
                {new Interval("chr17", 59784584, 59784615)},
                {new Interval("chr17", 59786025, 59786136)},
                {new Interval("chr17", 59787203, 59787494)},
                {new Interval("chr17", 59791235, 59791514)},
                {new Interval("chr17", 59794247, 59794502)},
                {new Interval("chr17", 59801884, 59802193)},
                {new Interval("chr17", 59804685, 59804982)},
                {new Interval("chr17", 59817352, 59817382)},
                {new Interval("chr17", 59817465, 59817532)},
                {new Interval("chr17", 59875754, 59875812)},
                {new Interval("chr17", 59875899, 59875944)},
                {new Interval("chr17", 59879183, 59879456)},
                {new Interval("chr17", 59883988, 59884276)},
                {new Interval("chr17", 59887398, 59887512)},
                {new Interval("chrX", 48774611, 48775058)},

        };
    }

    @Test
    public void testWriteChain() throws Exception {
        final OverlapDetector<Chain> chains = Chain.loadChains(CHAIN_FILE);
        File outFile = File.createTempFile("test.", ".chain");
        outFile.deleteOnExit();
        PrintWriter pw = new PrintWriter(outFile);
        final Map<Integer, Chain> originalChainMap = new TreeMap<Integer, Chain>();
        for (final Chain chain : chains.getAll()) {
            chain.write(pw);
            originalChainMap.put(chain.id, chain);
        }
        pw.close();

        final OverlapDetector<Chain> newChains = Chain.loadChains(outFile);
        final Map<Integer, Chain> newChainMap = new TreeMap<Integer, Chain>();
        for (final Chain chain : newChains.getAll()) {
            newChainMap.put(chain.id, chain);
        }
        Assert.assertEquals(newChainMap, originalChainMap);
    }
}
