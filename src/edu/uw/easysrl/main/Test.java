package edu.uw.easysrl.main;

import edu.stanford.nlp.util.StringUtils;

/**
 * Created by luheng on 12/22/15.
 */
public class Test {

    public static void main(String[] args) {
        String s1 = "saw_2(S[dcl]\\NP)/NP2 squirrel_4 0";
        String s2 = "a_333NP[nb]/N1 squirrel_444 0";
        String s3 = "$_9N/N[num]1 200,000_10 0";

        String[] info1 = s3.split("[_\\d]+");
        String[] info2 = s3.split("[^\\d]+");
        System.out.println(StringUtils.join(info1, " ... "));
        System.out.println(StringUtils.join(info2, " ... "));

        /** this doesn't work because word can contain numbers ... */
    }
}
