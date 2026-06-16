import SwiftUI

/// Animated tree that grows with the learning progress fraction (0…1).
/// Mirrors the Android `LearningTreeCard.TreeCanvas`: a seedling whose trunk
/// rises from the ground, sprouts a pair of branches past a quarter progress,
/// and reveals up to six leaf clusters bottom-to-top as personalisation
/// progresses, with a sun accent appearing past the halfway mark. Colours come
/// from the shared `AppTheme` palette so the illustration matches the rest of
/// the app.
struct LearningTreeView: View {
    /// 0.0 – 1.0
    let progress: Float

    @State private var animated: CGFloat = 0

    /// Leaf clusters as canvas fractions, anchored above the trunk top. Each
    /// reveals once `progress` crosses its threshold (lowest first), matching the
    /// Android `LEAF_CLUSTERS` table so the canopy fills out from the bottom up.
    private struct LeafCluster {
        let xFrac: CGFloat
        let yFrac: CGFloat
        let radiusFrac: CGFloat
        let threshold: CGFloat
    }

    private let leafClusters: [LeafCluster] = [
        LeafCluster(xFrac: 0.50, yFrac: 0.55, radiusFrac: 0.14, threshold: 0.05),
        LeafCluster(xFrac: 0.32, yFrac: 0.42, radiusFrac: 0.16, threshold: 0.20),
        LeafCluster(xFrac: 0.68, yFrac: 0.42, radiusFrac: 0.16, threshold: 0.35),
        LeafCluster(xFrac: 0.50, yFrac: 0.28, radiusFrac: 0.18, threshold: 0.55),
        LeafCluster(xFrac: 0.26, yFrac: 0.30, radiusFrac: 0.13, threshold: 0.75),
        LeafCluster(xFrac: 0.74, yFrac: 0.30, radiusFrac: 0.13, threshold: 0.90),
    ]

    var body: some View {
        Canvas { context, size in
            let p = max(0, min(1, animated))
            let w = size.width
            let h = size.height
            let groundY = h * 0.92
            let trunkCenterX = w * 0.5

            // Trunk grows from a visible seedling (0.30h) up to 0.85h.
            let trunkHeight = (0.30 + 0.55 * p) * h
            let trunkTopY = groundY - trunkHeight
            let trunkWidth = w * 0.10

            var trunk = Path()
            trunk.move(to: CGPoint(x: trunkCenterX, y: groundY))
            trunk.addLine(to: CGPoint(x: trunkCenterX, y: trunkTopY))
            context.stroke(
                trunk,
                with: .color(AppTheme.bark),
                style: StrokeStyle(lineWidth: trunkWidth, lineCap: .round)
            )

            // Branches appear once progress > 0.25.
            if p > 0.25 {
                let branchProgress = max(0, min(1, (p - 0.25) / 0.55))
                let branchLen = w * 0.22 * branchProgress
                let branchOriginY = trunkTopY + trunkHeight * 0.25
                let branchStroke = trunkWidth * 0.55

                var left = Path()
                left.move(to: CGPoint(x: trunkCenterX, y: branchOriginY))
                left.addLine(to: CGPoint(x: trunkCenterX - branchLen,
                                         y: branchOriginY - branchLen * 0.6))
                var right = Path()
                right.move(to: CGPoint(x: trunkCenterX, y: branchOriginY))
                right.addLine(to: CGPoint(x: trunkCenterX + branchLen,
                                          y: branchOriginY - branchLen * 0.6))
                let branchStyle = StrokeStyle(lineWidth: branchStroke, lineCap: .round)
                context.stroke(left, with: .color(AppTheme.bark), style: branchStyle)
                context.stroke(right, with: .color(AppTheme.bark), style: branchStyle)
            }

            // Leaf clusters, anchored above the trunk top and rising with it.
            let canopyTop = trunkTopY - h * 0.05
            let canopyHeight = trunkHeight * 0.85
            for cluster in leafClusters where p >= cluster.threshold {
                let localProgress = max(0, min(1, (p - cluster.threshold) / (1 - cluster.threshold)))
                let cx = w * cluster.xFrac
                let cy = canopyTop + canopyHeight * cluster.yFrac
                let radius = w * cluster.radiusFrac * (0.55 + 0.45 * localProgress)
                let alpha = min(1, 0.55 + 0.45 * localProgress)
                let color = AppTheme.lerp(AppTheme.primaryContainer, AppTheme.primary, p)
                let rect = CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect), with: .color(color.opacity(Double(alpha))))
            }

            // Sun accent in the top-right, fades in past 0.5.
            if p > 0.5 {
                let sunAlpha = max(0, min(1, (p - 0.5) / 0.5))
                let sunRadius = w * 0.07
                let rect = CGRect(x: w * 0.88 - sunRadius, y: h * 0.12 - sunRadius,
                                  width: sunRadius * 2, height: sunRadius * 2)
                context.fill(Path(ellipseIn: rect),
                             with: .color(AppTheme.tertiary.opacity(0.85 * Double(sunAlpha))))
            }
        }
        .frame(height: 88)
        .onAppear { withAnimation(.easeOut(duration: 0.8)) { animated = CGFloat(progress) } }
        .onChange(of: progress) { newValue in
            withAnimation(.easeOut(duration: 0.8)) { animated = CGFloat(newValue) }
        }
    }
}
