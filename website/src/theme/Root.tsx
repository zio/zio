import React from 'react';
import OrganizationSchema from '../components/SEOSchemas/OrganizationSchema';

export default function Root({ children }: { children: React.ReactNode }) {
  return (
    <>
      <OrganizationSchema />
      {children}
    </>
  );
}
