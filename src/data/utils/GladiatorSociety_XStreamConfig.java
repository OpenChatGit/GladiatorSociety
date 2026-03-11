package src.data.utils;

/*
import src.data.scripts.campaign.GladiatorSociety_Bounty;
import src.data.scripts.campaign.GladiatorSociety_BountyData;*/
import src.data.scripts.campaign.GladiatorSociety_Content;
import src.data.scripts.campaign.GladiatorSociety_EndlessContent;
import src.data.scripts.campaign.GladiatorSociety_FleetBattleContent;
import src.data.scripts.campaign.dataclass.GladiatorSociety_BountyData;
import src.data.scripts.campaign.dataclass.GladiatorSociety_DataShip;
import src.data.scripts.campaign.dataclass.GladiatorSociety_EndlessReward;

public class GladiatorSociety_XStreamConfig {

    public static void configureXStream(com.thoughtworks.xstream.XStream x) {
        x.alias("GladiatorSociety_Content", GladiatorSociety_Content.class);
        x.alias("GladiatorSociety_EndlessContent", GladiatorSociety_EndlessContent.class);
        x.alias("GladiatorSociety_FleetBattleContent", GladiatorSociety_FleetBattleContent.class);
        x.alias("GladiatorSociety_BountyData", GladiatorSociety_BountyData.class);
        x.alias("GladiatorSociety_DataShip", GladiatorSociety_DataShip.class);
        x.alias("GladiatorSociety_EndlessReward", GladiatorSociety_EndlessReward.class);
    }
}
