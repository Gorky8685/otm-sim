package tests;

import org.junit.Test;
import runner.OTMold;
import utils.OTMUtils;

public class TestVersion {

    @Test
    public void test_get_version(){
        System.out.println("otm-base: " + OTMUtils.getBaseGitHash());
        System.out.println("otm-sim: " + OTMold.getGitHash());
    }

}
