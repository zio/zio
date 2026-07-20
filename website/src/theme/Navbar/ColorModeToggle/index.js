import React from 'react';
import ColorModeToggle from '@theme-original/Navbar/ColorModeToggle';
import OnboardAgentNavbarButton from '@site/src/components/ui/OnboardAgentButton/NavbarButton';

// Render the compact Onboard Agent button immediately after the light/dark
// theme switch in the navbar.
export default function ColorModeToggleWrapper(props) {
  return (
    <>
      <ColorModeToggle {...props} />
      <OnboardAgentNavbarButton />
    </>
  );
}
