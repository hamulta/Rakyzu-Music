import 'package:flutter/material.dart';

class PrivacyPolicyPage extends StatelessWidget {
  const PrivacyPolicyPage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(title: const Text('Privacy Policy')),
      body: const SingleChildScrollView(
          padding: EdgeInsets.all(20),
          child: Text(
              'Rakyzu Music collects email, play history, ad impressions (Start.io App ID 207228132), payment via Midtrans (Merchant M046699444). Data stored in Supabase (tkryesvnaocysmpstbtj.supabase.co) & Cloudflare R2. No Server Key in client. Contact: privacy@rakyzu.com')));
}
