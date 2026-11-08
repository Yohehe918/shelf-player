/* Port of literalTitle/normaliseTitle from Kinema src/metadata/score.ts.
 * Copyright (c) 2026 Basswaves, MIT; upstream 9b61dc4cb28cc018c12eae13bdc712dace67ccd4.
 * Java regex/Unicode adaptation. Automatic matching uses literal, retaining articles. */
package local.shelf.player;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
final class TitleMatching {
    private static final Map<String,String> NUMBERS=new HashMap<>();
    static {String[][] values={{"one","two","three","four","five","six","seven","eight","nine","ten"},{"i","ii","iii","iv","v","vi","vii","viii","ix","x"}};for(String[] set:values)for(int i=0;i<set.length;i++)NUMBERS.put(set[i],String.valueOf(i+1));}
    private static final Pattern NUMBERED=Pattern.compile("\\b(part|chapter|vol|volume)\\s+(one|two|three|four|five|six|seven|eight|nine|ten|viii|vii|vi|iv|v|ix|x|iii|ii|i)\\b");
    static String literal(String raw){
        String s=Normalizer.normalize(Normalizer.normalize(raw,Normalizer.Form.NFKD).replaceAll("[\\u0300-\\u036f]",""),Normalizer.Form.NFC)
            .toLowerCase(Locale.ROOT).replace("&"," and ").replaceAll("[^\\p{L}\\p{M}\\p{N}\\s]"," ").replaceAll("\\s+"," ").trim();
        Matcher m=NUMBERED.matcher(s);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(m.group(1)+" "+NUMBERS.get(m.group(2))));m.appendTail(out);return out.toString();
    }
    static String normalized(String raw){return literal(raw).replaceFirst("^(the|a|an)\\s+","");}
}
