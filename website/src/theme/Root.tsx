import React from 'react';
import { useLocation } from '@docusaurus/router';
import OrganizationSchema from '../components/SEOSchemas/OrganizationSchema';
import FAQSchema from '../components/SEOSchemas/FAQSchema';

export default function Root({ children }) {
  const { pathname } = useLocation();
  const isFAQPage = pathname === '/faq' || pathname === '/faq/';

  return (
    <>
      <OrganizationSchema />
      {isFAQPage && <FAQSchema />}
      {children}
    </>
  );
}
