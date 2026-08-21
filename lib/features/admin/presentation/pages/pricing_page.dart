import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/admin_providers.dart';

class PricingPage extends ConsumerStatefulWidget {
  const PricingPage({super.key});
  @override
  ConsumerState<PricingPage> createState() => _PricingPageState();
}

class _PricingPageState extends ConsumerState<PricingPage> {
  final _monthly = TextEditingController();
  final _yearly = TextEditingController();
  bool _saving = false;
  @override
  void dispose() {
    _monthly.dispose();
    _yearly.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pricing = ref.watch(adminPricingProvider);
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Pricing Plans (Owner Only)',
            style: Theme.of(context)
                .textTheme
                .headlineSmall
                ?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        const Text(
            'Monthly/Yearly harga yang sebelumnya hardcoded di v0.7.1 kini data-driven.',
            style: TextStyle(color: Colors.grey, fontSize: 12)),
        const SizedBox(height: 16),
        pricing.when(
          data: (list) {
            final monthly = list.firstWhere((e) => e['name'] == 'monthly',
                orElse: () => {'price_idr': 49000});
            final yearly = list.firstWhere((e) => e['name'] == 'yearly',
                orElse: () => {'price_idr': 449000});
            _monthly.text = _monthly.text.isEmpty
                ? '${monthly['price_idr']}'
                : _monthly.text;
            _yearly.text =
                _yearly.text.isEmpty ? '${yearly['price_idr']}' : _yearly.text;
            return GlassCard(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  TextField(
                      controller: _monthly,
                      decoration:
                          const InputDecoration(labelText: 'Monthly Price IDR'),
                      keyboardType: TextInputType.number),
                  const SizedBox(height: 12),
                  TextField(
                      controller: _yearly,
                      decoration:
                          const InputDecoration(labelText: 'Yearly Price IDR'),
                      keyboardType: TextInputType.number),
                  const SizedBox(height: 16),
                  SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                          onPressed: _saving
                              ? null
                              : () async {
                                  setState(() => _saving = true);
                                  try {
                                    final repo =
                                        ref.read(adminRepositoryProvider);
                                    await repo.updatePricing(
                                        'monthly',
                                        int.parse(_monthly.text
                                            .replaceAll(RegExp('[^0-9]'), '')));
                                    await repo.updatePricing(
                                        'yearly',
                                        int.parse(_yearly.text
                                            .replaceAll(RegExp('[^0-9]'), '')));
                                    ref.invalidate(adminPricingProvider);
                                    if (mounted)
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(const SnackBar(
                                              content:
                                                  Text('Pricing updated')));
                                  } catch (e) {
                                    if (mounted)
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(SnackBar(
                                              content: Text('Failed: $e')));
                                  } finally {
                                    if (mounted)
                                      setState(() => _saving = false);
                                  }
                                },
                          child: _saving
                              ? const CupertinoActivityIndicator()
                              : const Text('Save (Owner only)'))),
                ],
              ),
            );
          },
          loading: () => const CupertinoActivityIndicator(),
          error: (e, _) => Text('Error $e'),
        ),
      ],
    );
  }
}
