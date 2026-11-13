package annina.sparkstrength.payloadpolicy;

import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * GENERATED FILE, DO NOT EDIT BY HAND. Generated from SparkFactionAPI {@code config/payload-policy/actions.tsv}
 * by {@code tools/payload_policy.py generate --mod strength}; {@code tools/payload_policy.py check} fails on any
 * difference. Each set holds the ids whose column in that table reads {@code deny}: the C2S payloads the profile
 * drops. Reasons for individual decisions are in the table's {@code note} column.
 * 生成文件，请勿手动编辑。由 SparkFactionAPI 的 {@code tools/payload_policy.py} 根据
 * {@code config/payload-policy/actions.tsv} 生成；任何差异都会使 {@code tools/payload_policy.py check} 失败。
 * 每个集合保存该表中对应列为 {@code deny} 的 id，即该配置丢弃的 C2S 数据包。各项决定的理由见表中的 {@code note} 列。
 */
public final class GeneratedPayloadPolicy {
    /** SHA-256 of the canonical table this file was generated from. / 生成本文件所用规范表的 SHA-256。 */
    public static final String CONTRACT_SHA256 = "06e172356273dd4403002c0bd14b180531632866e3ccef27c6b52bde865c9646";

    /** Column DAZE: 97 ids. / DAZE 列：97 个 id。 */
    public static final Set<Identifier> DAZE = Set.of(
            Identifier.of("noellesroles", "ability"),
            Identifier.of("noellesroles", "assassin_guess_role"),
            Identifier.of("noellesroles", "demon_hunter_shoot"),
            Identifier.of("noellesroles", "detective_investigate"),
            Identifier.of("noellesroles", "morph"),
            Identifier.of("noellesroles", "morph_corpse_toggle"),
            Identifier.of("noellesroles", "party_animal_buzz"),
            Identifier.of("noellesroles", "reporter_mark"),
            Identifier.of("noellesroles", "shadow_ally_request"),
            Identifier.of("noellesroles", "silencer_silence"),
            Identifier.of("noellesroles", "spirit_project"),
            Identifier.of("noellesroles", "swapper"),
            Identifier.of("noellesroles", "taotie_swallow"),
            Identifier.of("noellesroles", "vulture"),
            Identifier.of("sparkstrength", "approve_suspect_removal"),
            Identifier.of("sparkstrength", "bandit_revolver_shoot"),
            Identifier.of("sparkstrength", "call_tablet_meeting"),
            Identifier.of("sparkstrength", "cast_tablet_vote"),
            Identifier.of("sparkstrength", "complete_breaker_repair"),
            Identifier.of("sparkstrength", "confirm_tablet_vote"),
            Identifier.of("sparkstrength", "coroner_morph"),
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            Identifier.of("sparkstrength", "drone_pilot_action"),
            Identifier.of("sparkstrength", "drone_pilot_start"),
            Identifier.of("sparkstrength", "interrogate_detective_suspect"),
            Identifier.of("sparkstrength", "noisemaker_glow"),
            Identifier.of("sparkstrength", "partygoer_summon"),
            Identifier.of("sparkstrength", "partygoer_swallow"),
            Identifier.of("sparkstrength", "phantom_backpack_invisibility"),
            Identifier.of("sparkstrength", "professor_remote_feed"),
            Identifier.of("sparkstrength", "reporter_communication"),
            Identifier.of("sparkstrength", "serial_pistol_shoot"),
            Identifier.of("sparkstrength", "spirit_possess"),
            Identifier.of("sparkstrength", "taotie_head_fire"),
            Identifier.of("sparkstrength", "timekeeper_watch_mode"),
            Identifier.of("sparkstrength", "trigger_sabotage"),
            Identifier.of("sparkstrength", "vulture_super_curse"),
            Identifier.of("sparkwitch", "accountant_withdraw"),
            Identifier.of("sparkwitch", "black_raven_crow_drop_feather"),
            Identifier.of("sparkwitch", "black_raven_crow_move"),
            Identifier.of("sparkwitch", "black_raven_crow_toggle"),
            Identifier.of("sparkwitch", "black_raven_crow_use"),
            Identifier.of("sparkwitch", "confirm_judge_selection"),
            Identifier.of("sparkwitch", "confirm_natsume_target"),
            Identifier.of("sparkwitch", "confirm_prophecy"),
            Identifier.of("sparkwitch", "confirm_sherry_deduction"),
            Identifier.of("sparkwitch", "emma_factor"),
            Identifier.of("sparkwitch", "ferryman_ferry"),
            Identifier.of("sparkwitch", "ferryman_pardon"),
            Identifier.of("sparkwitch", "fire_crimson_skull"),
            Identifier.of("sparkwitch", "fire_death_ray"),
            Identifier.of("sparkwitch", "fire_m32a1"),
            Identifier.of("sparkwitch", "fire_potion_launcher"),
            Identifier.of("sparkwitch", "fire_usec_rifle"),
            Identifier.of("sparkwitch", "guardian"),
            Identifier.of("sparkwitch", "knight_duel_target"),
            Identifier.of("sparkwitch", "magician_ability"),
            Identifier.of("sparkwitch", "mini_wither_toggle"),
            Identifier.of("sparkwitch", "open_judge_selection"),
            Identifier.of("sparkwitch", "ragpicker_search"),
            Identifier.of("sparkwitch", "request_natsume_menu"),
            Identifier.of("sparkwitch", "request_prophecy"),
            Identifier.of("sparkwitch", "request_sherry_deduction"),
            Identifier.of("sparkwitch", "rift_gate_close"),
            Identifier.of("sparkwitch", "rift_hop"),
            Identifier.of("sparkwitch", "savior_pistol_shoot"),
            Identifier.of("sparkwitch", "seeker_car_recall"),
            Identifier.of("sparkwitch", "seeker_car_swallow"),
            Identifier.of("sparkwitch", "seeker_car_use"),
            Identifier.of("sparkwitch", "seeker_remote_open"),
            Identifier.of("sparkwitch", "select_black_raven_disguise"),
            Identifier.of("sparkwitch", "submit_medium_haunt_target"),
            Identifier.of("sparkwitch", "submit_medium_seance"),
            Identifier.of("sparkwitch", "submit_tarot_divination_selection"),
            Identifier.of("sparkwitch", "throw_kidnapper_body"),
            Identifier.of("sparkwitch", "use_apprentice_purify"),
            Identifier.of("sparkwitch", "use_athlete_dash"),
            Identifier.of("sparkwitch", "use_blind_attune"),
            Identifier.of("sparkwitch", "use_curser_ability"),
            Identifier.of("sparkwitch", "use_dominator_skill"),
            Identifier.of("sparkwitch", "use_fiend_dash"),
            Identifier.of("sparkwitch", "use_hanna_float"),
            Identifier.of("sparkwitch", "use_hiro_skill"),
            Identifier.of("sparkwitch", "use_medium_second_skill"),
            Identifier.of("sparkwitch", "use_medium_skill"),
            Identifier.of("sparkwitch", "use_noa_paint"),
            Identifier.of("sparkwitch", "use_orthopedist_skill"),
            Identifier.of("sparkwitch", "use_saboteur_skill"),
            Identifier.of("sparkwitch", "use_savior_skill"),
            Identifier.of("sparkwitch", "use_sherry_mighty_force"),
            Identifier.of("sparkwitch", "use_skill"),
            Identifier.of("sparkwitch", "use_specter_second_skill"),
            Identifier.of("sparkwitch", "use_specter_skill"),
            Identifier.of("sparkwitch", "usec_attachment"),
            Identifier.of("sparkwitch", "vendetta_knife_stab"),
            Identifier.of("wathe", "gunshoot"),
            Identifier.of("wathe", "knifestab"));

    /** Column CARRY_EXTRA: 2 ids. / CARRY_EXTRA 列：2 个 id。 */
    public static final Set<Identifier> CARRY_EXTRA = Set.of(
            Identifier.of("sparkwitch", "magician_target"),
            Identifier.of("sparkwitch", "swordfish_stab"));

    private GeneratedPayloadPolicy() {
    }
}
