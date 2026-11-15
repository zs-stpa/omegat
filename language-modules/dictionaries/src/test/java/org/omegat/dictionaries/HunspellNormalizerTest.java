/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2026 Stephan Pakebusch
               Home page: https://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.dictionaries;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Each test reproduces one upstream defect found in the LibreOffice and
 * wooorm collections and checks that the normalised pair loads in Lucene.
 *
 * @author stephan.pakebusch at zollsoft.de
 */
public class HunspellNormalizerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testWrongAffixCountIsRepaired() throws Exception {
        // hy, es_UY, ta_IN: header says one rule, three follow.
        HunspellNormalizer.Result result = convert(
                "SET UTF-8\nSFX A Y 1\nSFX A 0 s .\nSFX A 0 es .\nSFX A 0 n .\n", "1\nword/A\n");
        assertThat(result.affixLinesFixed).isEqualTo(1);
        assertThat(aff()).contains("SFX A Y 3");
        assertThat(spell("words")).isTrue();
    }

    @Test
    public void testMistypedKeywordAndJunkAfterCount() throws Exception {
        // la, mn: SFK/SFT inside a group; bn_BD: "SFX X Y 2$".
        HunspellNormalizer.Result result = convert(
                "SET UTF-8\nSFX k Y 1$\nSFX k 0 s .\nSFK k 0 es .\nSFX k 0 n .\n", "1\nword/k\n");
        assertThat(result.affixLinesFixed).isEqualTo(2);
        assertThat(aff()).contains("SFX k Y 3").doesNotContain("SFK").doesNotContain("$");
        assertThat(spell("wordes")).isTrue();
    }

    @Test
    public void testBomBeforeSetLine() throws Exception {
        // pt_BR: without this the pair was read as Latin-1 and double-encoded.
        byte[] aff = concat(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF },
                "SET UTF-8\nSFX à Y 1\nSFX à 0 ultra .\n".getBytes(StandardCharsets.UTF_8));
        HunspellNormalizer.Result result = convertBytes(aff, "1\nsóm/à\n".getBytes(StandardCharsets.UTF_8));
        assertThat(result.declaredEncoding).isEqualTo("UTF-8");
        assertThat(dic()).contains("sóm/à").doesNotContain("Ã");
        assertThat(spell("sómultra")).isTrue();
    }

    @Test
    public void testWrongSingleByteDeclarationOverUtf8Content() throws Exception {
        // id_ID: declared ISO8859-1, bytes are UTF-8.
        HunspellNormalizer.Result result = convertBytes("SET ISO8859-1\n".getBytes(StandardCharsets.UTF_8),
                "1\nécole\n".getBytes(StandardCharsets.UTF_8));
        assertThat(result.usedEncoding).isEqualTo("UTF-8");
        assertThat(dic()).contains("école");
    }

    @Test
    public void testLatin2SourceIsTranscoded() throws Exception {
        // hu (wooorm source), pl, sl: single-byte sources become UTF-8.
        Charset latin2 = Charset.forName("ISO-8859-2");
        HunspellNormalizer.Result result = convertBytes("SET ISO8859-2\n".getBytes(latin2),
                "1\nház\n".getBytes(latin2));
        assertThat(result.usedEncoding).isEqualTo("ISO-8859-2");
        assertThat(aff()).startsWith("SET UTF-8\n");
        assertThat(dic()).contains("ház");
        assertThat(spell("ház")).isTrue();
    }

    @Test
    public void testCountSharingItsLineWithComment() throws Exception {
        // fy, da: "299498 Copyright ..." as first line.
        HunspellNormalizer.Result result = convert("SET UTF-8\n", "2 # produced by someone\nalpha\nbeta\n");
        assertThat(result.declaredCount).isEqualTo(2);
        assertThat(result.entries).isEqualTo(2);
        assertThat(dic()).startsWith("2\nalpha\n");
    }

    @Test
    public void testStaleCountIsRecomputed() throws Exception {
        HunspellNormalizer.Result result = convert("SET UTF-8\n", "5\nalpha\nbeta\n\n");
        assertThat(result.declaredCount).isEqualTo(5);
        assertThat(result.entries).isEqualTo(2);
        assertThat(dic()).isEqualTo("2\nalpha\nbeta\n");
    }

    @Test
    public void testUndecodableRuleLinesKeepThePairVerbatim() throws Exception {
        // hu_HU in LibreOffice: Latin-2 flags under a UTF-8 declaration.
        byte[] aff = concat("SET UTF-8\nAF 1\nAF ".getBytes(StandardCharsets.UTF_8), new byte[] { (byte) 0xCB },
                "\n".getBytes(StandardCharsets.UTF_8));
        byte[] dic = "1\nword/1\n".getBytes(StandardCharsets.UTF_8);
        HunspellNormalizer.Result result = convertBytes(aff, dic);
        assertThat(result.verbatim).isTrue();
        assertThat(Files.readAllBytes(out("aff"))).isEqualTo(aff);
        assertThat(Files.readAllBytes(out("dic"))).isEqualTo(dic);
    }

    @Test
    public void testUndecodableCommentLinesAreReplaced() throws Exception {
        byte[] aff = concat("# by L".getBytes(StandardCharsets.UTF_8), new byte[] { (byte) 0xE1 },
                "szló\nSET UTF-8\n".getBytes(StandardCharsets.UTF_8));
        HunspellNormalizer.Result result = convertBytes(aff, "1\nword\n".getBytes(StandardCharsets.UTF_8));
        assertThat(result.verbatim).isFalse();
        assertThat(result.commentLinesReplaced).isEqualTo(1);
        assertThat(aff()).contains("SET UTF-8");
    }

    @Test
    public void testFlagsMayBeNonBreakingSpace() throws Exception {
        // pt_BR: a flag of U+00A0 must survive field splitting.
        List<String> lines = new ArrayList<>(Arrays.asList("PFX à  Y 1", "PFX à  0 ultra ."));
        int fixed = HunspellNormalizer.repairAffixCounts(lines);
        assertThat(fixed).isEqualTo(0);
        assertThat(lines.get(0)).isEqualTo("PFX à  Y 1");
    }

    private HunspellNormalizer.Result convert(String aff, String dic) throws IOException {
        return convertBytes(aff.getBytes(StandardCharsets.UTF_8), dic.getBytes(StandardCharsets.UTF_8));
    }

    private HunspellNormalizer.Result convertBytes(byte[] aff, byte[] dic) throws IOException {
        Path in = tmp.newFolder("in").toPath();
        Files.write(in.resolve("x.aff"), aff);
        Files.write(in.resolve("x.dic"), dic);
        return HunspellNormalizer.convert(in.resolve("x.aff"), in.resolve("x.dic"), out("aff"), out("dic"));
    }

    private Path out(String ext) {
        return tmp.getRoot().toPath().resolve("out").resolve("x." + ext);
    }

    private String aff() throws IOException {
        return Files.readString(out("aff"), StandardCharsets.UTF_8);
    }

    private String dic() throws IOException {
        return Files.readString(out("dic"), StandardCharsets.UTF_8);
    }

    private boolean spell(String word) throws Exception {
        try (var aff = Files.newInputStream(out("aff")); var dic = Files.newInputStream(out("dic"))) {
            Dictionary dictionary = new Dictionary(new ByteBuffersDirectory(), "t", aff, List.of(dic), true);
            return new Hunspell(dictionary).spell(word);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] p : parts) {
            length += p.length;
        }
        byte[] out = new byte[length];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
