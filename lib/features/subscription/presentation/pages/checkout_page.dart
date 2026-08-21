import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

class CheckoutPage extends StatefulWidget {
  const CheckoutPage({super.key, required this.snapToken, required this.redirectUrl, required this.plan});
  final String? snapToken;
  final String? redirectUrl;
  final String plan;
  @override
  State<CheckoutPage> createState() => _CheckoutPageState();
}

class _CheckoutPageState extends State<CheckoutPage> {
  WebViewController? _ctrl;
  bool _handled = false;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      if (widget.redirectUrl != null) launchUrl(Uri.parse(widget.redirectUrl!), mode: LaunchMode.externalApplication);
    } else if (widget.redirectUrl != null) {
      _ctrl = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(NavigationDelegate(
          onNavigationRequest: (req) {
            final url = req.url;
            if (url.contains('payment/finish') || url.contains('status_code')) {
              if (!_handled) {
                _handled = true;
                // Simple heuristic: if url contains transaction_status=settlement -> success
                String outcome = 'pending';
                if (url.contains('settlement') || url.contains('capture')) outcome = 'success';
                if (url.contains('deny') || url.contains('failure') || url.contains('cancel')) outcome = 'failure';
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted) context.go('/checkout/result?status=$outcome');
                });
                return NavigationDecision.prevent;
              }
            }
            return NavigationDecision.navigate;
          },
        ))
        ..loadRequest(Uri.parse(widget.redirectUrl!));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) {
      return Scaffold(appBar: AppBar(title: const Text('Checkout')), body: Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [const Text('Redirecting to Midtrans Snap...'), const SizedBox(height: 12), if (widget.redirectUrl != null) ElevatedButton(onPressed: () => launchUrl(Uri.parse(widget.redirectUrl!), mode: LaunchMode.externalApplication), child: const Text('Open Payment Page'))])));
    }
    if (_ctrl == null) return Scaffold(appBar: AppBar(title: const Text('Checkout')), body: const Center(child: Text('No snap token')));
    return Scaffold(appBar: AppBar(title: Text('Checkout - ${widget.plan}')), body: WebViewWidget(controller: _ctrl!));
  }
}

class CheckoutResultPage extends StatelessWidget {
  const CheckoutResultPage({super.key, required this.status});
  final String status;
  @override
  Widget build(BuildContext context) {
    final isSuccess = status == 'success';
    final isPending = status == 'pending';
    return Scaffold(
      appBar: AppBar(title: const Text('Payment Result')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(isSuccess ? Icons.check_circle : isPending ? Icons.hourglass_top : Icons.cancel, size: 64, color: isSuccess ? Colors.green : isPending ? Colors.orange : Colors.red),
            const SizedBox(height: 16),
            Text(isSuccess ? 'Payment Success!' : isPending ? 'Payment Pending' : 'Payment Failed/Cancelled', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(isSuccess ? 'Premium akan aktif setelah webhook memverifikasi.' : isPending ? 'Selesaikan pembayaran di Midtrans.' : 'Coba lagi atau pilih metode lain.', textAlign: TextAlign.center),
            const SizedBox(height: 20),
            ElevatedButton(onPressed: () => context.go('/main'), child: const Text('Back to Home')),
          ]),
        ),
      ),
    );
  }
}
