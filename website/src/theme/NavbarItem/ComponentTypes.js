import ComponentTypes from '@theme-original/NavbarItem/ComponentTypes';
import OnboardAgentNavbarButton from '@site/src/components/ui/OnboardAgentButton/NavbarButton';

// Register a custom navbar item type so docusaurus.config.js can place the
// compact "Onboard Agent" button in the navbar via { type: 'custom-onboardAgent' }.
export default {
  ...ComponentTypes,
  'custom-onboardAgent': OnboardAgentNavbarButton,
};
