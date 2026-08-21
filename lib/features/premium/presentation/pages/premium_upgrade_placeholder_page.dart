import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/subscription_config.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../admin/providers/admin_providers.dart';
import '../../../subscription/providers/subscription_provider.dart';

/// Full Upgrade to Premium page — replace placeholder v0.6.5.
/// Harga default: Monthly Rp 49.000, Yearly Rp 449.000 (asumsi Pasar Indonesia, bisa diubah Owner di 0.8.x).
class PremiumUpgradePlaceholderPage extends ConsumerStatefulWidget {
  const PremiumUpgradePlaceholderPage({super.key});
  @override
  ConsumerState<PremiumUpgradePlaceholderPage> createState() => _PremiumUpgradePlaceholderPageState();
}

class _PremiumUpgradePlaceholderPageState extends ConsumerState<PremiumUpgradePlaceholderPage> {
  String _plan = 'monthly';
  bool _loading = false;

  Future<void> _checkout() async {
    setState(() => _loading = true);
    try {
      final repo = ref.read(subscriptionRepositoryProvider);
      final res = await repo.requestSnapToken(planType: _plan);
      final token = res['snap_token'] as String?;
      final redirect = res['redirect_url'] as String?;
      if (!mounted) return;
      if (token == null && redirect == null) throw Exception('No snap_token');
      context.push('/premium/checkout', extra: {'snap_token': token, 'redirect_url': redirect, 'plan': _plan});
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Checkout gagal: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final pricing = ref.watch(adminPricingProvider);
    final monthlyPrice = pricing.valueOrNull?.firstWhere((e)=> e['name']=='monthly', orElse:()=> {'price_idr': SubscriptionConfig.monthlyPriceIdr})['price_idr'] as int? ?? SubscriptionConfig.monthlyPriceIdr;
    final yearlyPrice = pricing.valueOrNull?.firstWhere((e)=> e['name']=='yearly', orElse:()=> {'price_idr': SubscriptionConfig.yearlyPriceIdr})['price_idr'] as int? ?? SubscriptionConfig.yearlyPriceIdr;
    const benefits = [
      ['Feature', 'Free', 'Premium'],
      ['Ad-free listening', '✕', '✓'],
      ['Unlimited skips', '6/jam', '✓ Unlimited'],
      ['Offline download', '✕', '✓'],
      ['Audio quality', 'Standard', 'High'],
    ];
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(gradient: isDark ? AppColors.darkBackgroundGradient : AppColors.lightBackgroundGradient),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Row(children: [IconButton(onPressed: () => context.pop(), icon: const Icon(CupertinoIcons.back)), Text('Upgrade to Premium', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700))]),
              const SizedBox(height: 16),
              GlassCard(
                padding: const EdgeInsets.all(20),
                child: Column(children: [
                  Container(width: 64, height: 64, decoration: const BoxDecoration(shape: BoxShape.circle, gradient: AppColors.premiumGradient), child: const Icon(CupertinoIcons.star_fill, color: Colors.white, size: 28)),
                  const SizedBox(height: 12),
                  Text('Your Sound, Without Limits.', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800), textAlign: TextAlign.center),
                  const SizedBox(height: 16),
                  // Plan selector — now data-driven dari pricing_plans
                  Row(children: [
                    Expanded(child: _PlanCard(label: 'Monthly', price: '${SubscriptionConfig.formatIdr(monthlyPrice)}/bulan', selected: _plan == 'monthly', onTap: () => setState(() => _plan = 'monthly'))),
                    const SizedBox(width: 12),
                    Expanded(child: _PlanCard(label: 'Yearly', price: '${SubscriptionConfig.formatIdr(yearlyPrice)}/tahun\nHemat 24%', selected: _plan == 'yearly', onTap: () => setState(() => _plan = 'yearly'))),
                  ],),
                  const SizedBox(height: 16),
                  // Benefit table
                  Table(
                    border: TableBorder.all(color: AppColors.textSecondary.withOpacity(0.15), borderRadius: BorderRadius.circular(8)),
                    children: benefits.map((row) => TableRow(children: row.map((c) => Padding(padding: const EdgeInsets.all(8), child: Text(c, style: TextStyle(fontWeight: row == benefits.first ? FontWeight.w700 : FontWeight.w400, fontSize: 12), textAlign: TextAlign.center))).toList())).toList(),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(width: double.infinity, child: CupertinoButton.filled(onPressed: _loading ? null : _checkout, child: _loading ? const CupertinoActivityIndicator(color: Colors.white) : Text('Continue — ${SubscriptionConfig.formatIdr(_plan == 'monthly' ? monthlyPrice : yearlyPrice)}'))),
                  const SizedBox(height: 8),
                  Text('Midtrans Sandbox — no real charge', style: TextStyle(color: AppColors.textSecondary.withOpacity(0.7), fontSize: 11), textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  Consumer(builder: (context, ref, _) {
                    final active = ref.watch(activeSubscriptionProvider).valueOrNull;
                    if (active == null) return const SizedBox.shrink();
                    return Column(children: [
                      Text('Active: ${active.planType} until ${active.endDate?.toLocal().toString().split(' ').first ?? '-'}', style: const TextStyle(fontSize: 12, color: AppColors.textSecondary), textAlign: TextAlign.center),
                      const SizedBox(height: 8),
                      CupertinoButton(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        color: AppColors.textSecondary.withOpacity(0.15),
                        child: const Text('Cancel Auto-Renewal', style: TextStyle(color: AppColors.textSecondary, fontSize: 12)),
                        onPressed: () async {
                          final ok = await showCupertinoDialog<bool>(context: context, builder: (c) => CupertinoAlertDialog(title: const Text('Cancel subscription?'), content: const Text('You will remain Premium until end_date, then downgrade to Free.'), actions: [CupertinoDialogAction(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')), CupertinoDialogAction(isDestructiveAction: true, onPressed: () => Navigator.pop(c, true), child: const Text('Cancel'))]));
                          if (ok == true) {
                            try {
                              await ref.read(subscriptionRepositoryProvider).cancelSubscription(active.id);
                              ref.invalidate(mySubscriptionsProvider);
                              ref.invalidate(activeSubscriptionProvider);
                              if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Cancelled — remains Premium until expiry')));
                            } catch (e) {
                              if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Failed: $e')));
                            }
                          }
                        },
                      ),
                    ],);
                  },),
                ],),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => context.push('/profile/transactions'), child: const Text('View transaction history')),
            ],
          ),
        ),
      ),
    );
  }
}

class _PlanCard extends StatelessWidget {
  const _PlanCard({required this.label, required this.price, required this.selected, required this.onTap});
  final String label;
  final String price;
  final bool selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          border: Border.all(color: selected ? AppColors.azureMistDeep : AppColors.textSecondary.withOpacity(0.2), width: selected ? 2 : 1),
          borderRadius: BorderRadius.circular(12),
          color: selected ? AppColors.azureMistDeep.withOpacity(0.08) : null,
        ),
        child: Column(children: [Text(label, style: TextStyle(fontWeight: FontWeight.w700, color: selected ? AppColors.azureMistDeep : null)), const SizedBox(height: 4), Text(price, textAlign: TextAlign.center, style: const TextStyle(fontSize: 12))]),
      ),
    );
  }
}
