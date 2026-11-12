package ba.slotbook.organization;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Id> {
}
