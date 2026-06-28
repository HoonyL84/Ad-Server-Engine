package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;

import java.util.List;

public interface AdMatcher {

    List<AdDocument> match(List<AdDocument> candidates, UserProfile profile, String slotId);
}
