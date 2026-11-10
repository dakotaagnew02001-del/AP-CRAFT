# v5.4 — Ground vehicle curb assist

- Ground vehicle boat prototypes now attempt to climb a one-block obstruction when driven against it.
- The assist checks horizontal collision, ground contact, forward motion, clearance above the obstacle, and a supporting landing surface before moving the vehicle.
- Does not apply to aircraft or unoccupied/unowned vehicles; avoids blindly teleporting through walls or across unsupported gaps.
- Existing original utility carts and pickups are included.
- Still uses boat placeholders. This does **not** make vehicles capable of climbing every terrain shape; fences, walls, steep slopes, and irregular blocks may still stop them.
- Not compiled or tested in Minecraft. Test on a 1-block ledge, 2-block wall, fence, stair, and drop-off before calling it reliable.
