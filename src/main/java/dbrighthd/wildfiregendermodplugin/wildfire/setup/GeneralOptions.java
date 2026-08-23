package dbrighthd.wildfiregendermodplugin.wildfire.setup;

/**
 * @author winnpixie
 */
public record GeneralOptions(GenderIdentities genderIdentity,
                            boolean hurtSounds,
                            float voicePitch,
                            boolean showInArmor,
                            boolean holidayThemes) {
    public static class Builder {
        private GenderIdentities genderIdentity;
        private boolean hurtSounds;
        private float voicePitch;
        private boolean showInArmor;
        private boolean holidayThemes = true;

        public Builder setGenderIdentity(GenderIdentities genderIdentity) {
            this.genderIdentity = genderIdentity;
            return this;
        }

        public Builder setHurtSounds(boolean hurtSounds) {
            this.hurtSounds = hurtSounds;
            return this;
        }

        public Builder setVoicePitch(float voicePitch) {
            this.voicePitch = voicePitch;
            return this;
        }

        public Builder setShowInArmor(boolean showInArmor) {
            this.showInArmor = showInArmor;
            return this;
        }

        public Builder setHolidayThemes(boolean holidayThemes) {
            this.holidayThemes = holidayThemes;
            return this;
        }

        public GeneralOptions create() {
            return new GeneralOptions(genderIdentity, hurtSounds, voicePitch, showInArmor, holidayThemes);
        }
    }
}
