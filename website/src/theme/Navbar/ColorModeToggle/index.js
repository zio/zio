import React from 'react';
import ColorModeToggle from '@theme-original/Navbar/ColorModeToggle';
import OnboardAgentNavbarButton from '@site/src/components/ui/OnboardAgentButton/NavbarButton';

// Render the compact Onboard Agent button just before the light/dark theme
// switch — i.e. between the GitHub link (last right item) and the toggle.
export default function ColorModeToggleWrapper(props) {
  return (
    <>
      <OnboardAgentNavbarButton />
      <ColorModeToggle {...props} />
    </>
  );
}
