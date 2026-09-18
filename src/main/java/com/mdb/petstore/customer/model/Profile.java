package com.mdb.petstore.customer.model;

public class Profile {

    private String languagePreference;
    private boolean bannerPreference;
    private boolean linkPreference;

    public Profile() {
    }

    public String getLanguagePreference() {
        return languagePreference;
    }

    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }

    public boolean isBannerPreference() {
        return bannerPreference;
    }

    public void setBannerPreference(boolean bannerPreference) {
        this.bannerPreference = bannerPreference;
    }

    public boolean isLinkPreference() {
        return linkPreference;
    }

    public void setLinkPreference(boolean linkPreference) {
        this.linkPreference = linkPreference;
    }
}
